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
    
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

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

    public YouTubeService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    public List<YouTubeVideo> fetchLatestVideos(int limit) {
        List<YouTubeVideo> allVideos = new ArrayList<>();
        
        for (ChannelInfo channel : CHANNELS) {
            try {
                List<YouTubeVideo> channelVideos = fetchVideosFromChannel(channel, limit);
                allVideos.addAll(channelVideos);
            } catch (Exception e) {
                // Continue with other channels if one fails
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
        
        // Sort by published date (most recent first) and limit results
        return allVideos.stream()
                .sorted((a, b) -> b.getPublishedDate().compareTo(a.getPublishedDate()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<YouTubeVideo> fetchVideosFromChannel(ChannelInfo channel, int limit) {
        String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channel.getChannelId();
        
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
            }, MAX_ATTEMPTS, INITIAL_BACKOFF, RETRY_PREDICATE);

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