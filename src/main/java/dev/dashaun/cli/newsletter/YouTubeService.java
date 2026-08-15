package dev.dashaun.cli.newsletter;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class YouTubeService {

    private final WebClient webClient;
    
    // YouTube's feed endpoint can stay 404 for tens of seconds; defaults of 3 attempts /
    // 2s backoff (6s window) routinely miss the recovery. 5 attempts at 5s → 10s → 20s → 40s
    // gives a ~75s window per channel without making the job absurdly long.
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(5);

    // Every channel must contribute a video. Per-request retries all happen inside one ~75s
    // window; when a channel is in a longer outage that window is simply too early. A sweep
    // re-runs the whole fetch for the channels that came back with nothing, after a pause,
    // which puts the next attempt minutes away from the first instead of seconds.
    private static final int DEFAULT_CHANNEL_SWEEPS = 3;
    private static final Duration DEFAULT_SWEEP_PAUSE = Duration.ofSeconds(20);

    // Sweeps multiply the worst case (channels x attempts x sweeps), so cap the wall clock:
    // a new sweep only starts if the budget still has room. A hung job helps nobody.
    static final Duration MAX_TOTAL_DURATION = Duration.ofMinutes(5);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final int channelSweeps;
    private final Duration sweepPause;

    // YouTube feeds always carry a published date, but a null must not blow up the sort.
    static final Comparator<YouTubeVideo> BY_DATE_DESC = Comparator.comparing(
            YouTubeVideo::getPublishedDate, Comparator.nullsLast(Comparator.reverseOrder()));

    // YouTube's feed endpoint occasionally returns transient 404s that resolve on retry.
    static final Predicate<Exception> RETRY_PREDICATE = e -> {
        if (RetryUtils.isRetryableException(e)) {
            return true;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException wcre && wcre.getStatusCode().value() == 404) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    };
    
    // Channel URLs mapped to their RSS feed URLs  
    private static final List<ChannelInfo> CHANNELS = List.of(
        new ChannelInfo("Coffee + Software", "UCjcceQmjS4DKBW_J_1UANow"),
        new ChannelInfo("SpringSourceDev", "UC7yfnfvEUlXUIfm8rGLwZdA"), 
        new ChannelInfo("Dan Vega", "UCc98QQw1D-y38wg6mO3w4MQ")
    );

    static final String DEFAULT_FEED_BASE_URL = "https://www.youtube.com/feeds/videos.xml";

    // Reactor Netty's default agent gets throttled harder than a named client on the
    // shared CI egress addresses, which is where the transient 404s cluster.
    private static final String USER_AGENT =
            "newsletter-cli (+https://github.com/dashaun-tanzu/newsletter-cli)";

    private final String feedBaseUrl;

    public YouTubeService() {
        this(DEFAULT_FEED_BASE_URL, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF,
                DEFAULT_CHANNEL_SWEEPS, DEFAULT_SWEEP_PAUSE);
    }

    YouTubeService(String feedBaseUrl, int maxAttempts, Duration initialBackoff,
                   int channelSweeps, Duration sweepPause) {
        this.feedBaseUrl = feedBaseUrl;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.channelSweeps = channelSweeps;
        this.sweepPause = sweepPause;
        this.webClient = WebClient.builder()
                .defaultHeader(org.springframework.http.HttpHeaders.USER_AGENT, USER_AGENT)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    /**
     * {@link #fetchLatest(int)} for callers that only want the video list. Prefer
     * {@code fetchLatest} where a channel coming back empty needs to be acted on.
     */
    public List<YouTubeVideo> fetchLatestVideos(int limit) {
        return fetchLatest(limit).videos();
    }

    /**
     * Fetches videos across all channels, guaranteeing every channel that answers is
     * represented by at least one video, and reporting the channels that never produced one.
     *
     * <p>Two layers of retry sit under this: {@link RetryUtils} retries each HTTP request, and
     * on top of that a channel that still produced nothing is swept again — the whole fetch
     * re-run after a pause — until it yields a video, the sweeps run out, or the overall time
     * budget does.
     *
     * <p>If <em>every</em> channel fails that is an outage, not an empty result: it throws
     * rather than reporting "0 videos" to a caller that would happily publish an empty section.
     */
    public FetchResult fetchLatest(int limit) {
        if (limit <= 0) {
            return new FetchResult(new ArrayList<>(), new ArrayList<>());
        }

        // Insertion-ordered so the reserved-slot selection below is deterministic.
        Map<String, List<YouTubeVideo>> byChannel = new LinkedHashMap<>();
        List<ChannelInfo> pending = new ArrayList<>(CHANNELS);
        long deadline = System.nanoTime() + MAX_TOTAL_DURATION.toNanos();

        for (int sweep = 1; sweep <= channelSweeps && !pending.isEmpty(); sweep++) {
            if (sweep > 1) {
                if (System.nanoTime() >= deadline) {
                    System.err.println("Giving up on " + pending.size()
                            + " YouTube channel(s): " + MAX_TOTAL_DURATION.toSeconds()
                            + "s budget exhausted before sweep " + sweep);
                    break;
                }
                System.err.println("Retrying " + pending.size() + " YouTube channel(s) in "
                        + sweepPause.toSeconds() + "s (sweep " + sweep + " of " + channelSweeps + ")");
                pause(sweepPause);
            }

            List<ChannelInfo> stillPending = new ArrayList<>();
            for (ChannelInfo channel : pending) {
                try {
                    List<YouTubeVideo> channelVideos = fetchVideosFromChannel(channel, limit);
                    if (channelVideos.isEmpty()) {
                        // A 200 with nothing usable is as bad as a failure for our purposes:
                        // the channel would be missing from the newsletter either way.
                        stillPending.add(channel);
                        System.err.println("No usable videos from " + channel.getName()
                                + " (sweep " + sweep + ")");
                    } else {
                        byChannel.put(channel.getName(), channelVideos);
                    }
                } catch (Exception e) {
                    stillPending.add(channel);
                    logChannelFailure(channel, sweep, e);
                }
            }
            pending = stillPending;
        }

        List<String> missingChannels = pending.stream().map(ChannelInfo::getName).toList();

        if (byChannel.isEmpty()) {
            throw new YouTubeUnavailableException(
                    "all " + CHANNELS.size() + " channel feeds failed after retries ("
                            + String.join(", ", missingChannels) + ")");
        }

        return new FetchResult(selectVideos(byChannel, limit), new ArrayList<>(missingChannels));
    }

    /**
     * Picks the videos to publish: one reserved slot per channel first, then the most recent of
     * whatever is left over. A plain "latest N across all channels" lets one prolific channel
     * take every slot, which looks exactly like the quiet channels having failed.
     */
    static List<YouTubeVideo> selectVideos(Map<String, List<YouTubeVideo>> byChannel, int limit) {
        List<YouTubeVideo> selected = new ArrayList<>();
        List<YouTubeVideo> leftovers = new ArrayList<>();

        for (List<YouTubeVideo> channelVideos : byChannel.values()) {
            List<YouTubeVideo> sorted = channelVideos.stream().sorted(BY_DATE_DESC).toList();
            selected.add(sorted.get(0));
            leftovers.addAll(sorted.subList(1, sorted.size()));
        }

        selected.sort(BY_DATE_DESC);
        if (selected.size() >= limit) {
            // More channels than slots — keep the most recent picks; nothing left to fill.
            return new ArrayList<>(selected.subList(0, limit));
        }

        leftovers.sort(BY_DATE_DESC);
        for (YouTubeVideo video : leftovers) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(video);
        }

        selected.sort(BY_DATE_DESC);
        return selected;
    }

    private void logChannelFailure(ChannelInfo channel, int sweep, Exception e) {
        System.err.println("Failed to fetch videos from " + channel.getName() + " (sweep " + sweep
                + "): " + e.getClass().getSimpleName() + ": " + e.getMessage());
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != e) {
            System.err.println("  root cause: " + root.getClass().getName() + ": " + root.getMessage());
        }
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted between YouTube channel sweeps", ie);
        }
    }

    /**
     * The videos to publish plus the channels that produced none, so the caller can fail the
     * run loudly instead of quietly shipping a newsletter with a channel missing.
     */
    public record FetchResult(List<YouTubeVideo> videos, List<String> missingChannels) {
        public boolean isComplete() {
            return missingChannels.isEmpty();
        }
    }

    /** Thrown when every channel feed fails, i.e. the section cannot be built at all. */
    public static class YouTubeUnavailableException extends RuntimeException {
        public YouTubeUnavailableException(String message) {
            super(message);
        }
    }

    private List<YouTubeVideo> fetchVideosFromChannel(ChannelInfo channel, int limit) {
        String rssUrl = feedBaseUrl + "?channel_id=" + channel.getChannelId();

        try {
            String rssContent = RetryUtils.executeWithRetry(new Callable<String>() {
                @Override
                public String call() {
                    return webClient.get()
                            .uri(rssUrl)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(30))
                            .block();
                }
            }, maxAttempts, initialBackoff, RETRY_PREDICATE);

            SyndFeed feed = parseRssContent(rssContent);
            if (feed == null) {
                return new ArrayList<>();
            }

            return feed.getEntries().stream()
                    .filter(entry -> !isShort(entry))
                    .limit(limit)
                    .map(entry -> convertToYouTubeVideo(entry, channel.getName()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch videos from " + channel.getName() + ": " + e.getMessage(), e);
        }
    }

    static boolean isShort(SyndEntry entry) {
        String link = entry.getLink();
        return link != null && link.contains("/shorts/");
    }

    private SyndFeed parseRssContent(String xmlContent) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8));
            XmlReader xmlReader = new XmlReader(inputStream);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(xmlReader);
        } catch (Exception e) {
            return null;
        }
    }

    private YouTubeVideo convertToYouTubeVideo(SyndEntry entry, String channelName) {
        LocalDateTime publishedDate = null;
        if (entry.getPublishedDate() != null) {
            publishedDate = entry.getPublishedDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return new YouTubeVideo(
                entry.getTitle(),
                entry.getLink(),
                channelName,
                publishedDate
        );
    }

    public static class YouTubeVideo {
        private final String title;
        private final String link;
        private final String channelName;
        private final LocalDateTime publishedDate;

        public YouTubeVideo(String title, String link, String channelName, LocalDateTime publishedDate) {
            this.title = title;
            this.link = link;
            this.channelName = channelName;
            this.publishedDate = publishedDate;
        }

        public String getTitle() { return title; }
        public String getLink() { return link; }
        public String getChannelName() { return channelName; }
        public LocalDateTime getPublishedDate() { return publishedDate; }

        @Override
        public String toString() {
            return String.format("- [%s](%s) - %s", title, link, channelName);
        }
    }

    private static class ChannelInfo {
        private final String name;
        private final String channelId;

        public ChannelInfo(String name, String channelId) {
            this.name = name;
            this.channelId = channelId;
        }

        public String getName() { return name; }
        public String getChannelId() { return channelId; }
    }
}