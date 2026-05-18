package dev.dashaun.cli.newsletter;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Service
public class RssService {

    private final WebClient webClient;

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

    public RssService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    private SyndFeed parseRssContent(String xmlContent) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8));
            XmlReader xmlReader = new XmlReader(inputStream);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(xmlReader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RSS content: " + e.getMessage(), e);
        }
    }

    public List<NewsItem> fetchLatestNews(List<String> rssUrls, int limit) {
        Map<String, NewsItem> uniqueByLink = new LinkedHashMap<>();
        for (String url : rssUrls) {
            try {
                for (NewsItem item : fetchLatestNews(url, Integer.MAX_VALUE)) {
                    uniqueByLink.putIfAbsent(item.link(), item);
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch " + url + ": " + e.getMessage());
            }
        }
        Comparator<NewsItem> byDateDesc = Comparator.comparing(
                NewsItem::publishedDate,
                Comparator.nullsLast(Comparator.reverseOrder()));
        return uniqueByLink.values().stream()
                .sorted(byDateDesc)
                .limit(limit)
                .toList();
    }

    public List<NewsItem> fetchLatestNews(String rssUrl, int limit) {
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
            }, MAX_ATTEMPTS, INITIAL_BACKOFF);

            SyndFeed feed = parseRssContent(rssContent);
            
            return feed.getEntries().stream()
                    .limit(limit)
                    .map(this::convertToNewsItem)
                    .toList();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch RSS feed: " + e.getMessage(), e);
        }
    }

    private NewsItem convertToNewsItem(SyndEntry entry) {
        LocalDateTime publishedDate = null;
        if (entry.getPublishedDate() != null) {
            publishedDate = entry.getPublishedDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return new NewsItem(
                entry.getTitle(),
                entry.getLink(),
                publishedDate
        );
    }

    public record NewsItem(String title, String link, LocalDateTime publishedDate) {
        @Override
        public String toString() {
            return String.format("- [%s](%s)", title, link);
        }
    }
}
