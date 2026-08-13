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
import java.util.List;
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

    private final int maxAttempts;
    private final Duration initialBackoff;

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
        this(DEFAULT_FEED_BASE_URL, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF);
    }

    YouTubeService(String feedBaseUrl, int maxAttempts, Duration initialBackoff) {
        this.feedBaseUrl = feedBaseUrl;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.webClient = WebClient.builder()
                .defaultHeader(org.springframework.http.HttpHeaders.USER_AGENT, USER_AGENT)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    /**
     * Fetches videos across all channels. A channel that fails is skipped so one bad feed
     * cannot blank the section, but if <em>every</em> channel fails that is an outage, not an
     * empty result — it throws rather than reporting "0 videos" to a caller that would happily
     * publish an empty section.
     */
    public List<YouTubeVideo> fetchLatestVideos(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        List<YouTubeVideo> allVideos = new ArrayList<>();
        List<String> failedChannels = new ArrayList<>();

        for (ChannelInfo channel : CHANNELS) {
            try {
                List<YouTubeVideo> channelVideos = fetchVideosFromChannel(channel, limit);
                allVideos.addAll(channelVideos);
            } catch (Exception e) {
                // Continue with other channels if one fails
                failedChannels.add(channel.getName());
                System.err.println("Failed to fetch videos from " + channel.getName() + ": "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                if (root != e) {
                    System.err.println("  root cause: " + root.getClass().getName() + ": " + root.getMessage());
                }
            }
        }

        if (failedChannels.size() == CHANNELS.size()) {
            throw new YouTubeUnavailableException(
                    "all " + CHANNELS.size() + " channel feeds failed after retries ("
                            + String.join(", ", failedChannels) + ")");
        }

        // Sort by published date (most recent first) and limit results
        return allVideos.stream()
                .sorted((a, b) -> b.getPublishedDate().compareTo(a.getPublishedDate()))
                .limit(limit)
                .collect(Collectors.toList());
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