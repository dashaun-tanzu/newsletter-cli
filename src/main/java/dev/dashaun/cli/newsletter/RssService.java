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
import java.util.List;

@Service
public class RssService {

    private final WebClient webClient;

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

    public List<NewsItem> fetchLatestNews(String rssUrl, int limit) {
        try {
            String rssContent = webClient.get()
                    .uri(rssUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

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
