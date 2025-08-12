package dev.dashaun.cli.newsletter;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RssService {

    private final WebClient webClient;

    public RssService() {
        this.webClient = WebClient.builder().build();
    }

    public SyndFeed parseRssContent(String xmlContent) {
        try {
            // For Rome's XmlReader, we need to convert to an InputStream first
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8));

            // Now we can use Rome's XmlReader
            XmlReader xmlReader = new XmlReader(inputStream);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(xmlReader);
        } catch (Exception e) {
            // Handle exceptions
            return null;
        }
    }

    public List<NewsItem> fetchLatestNews(String rssUrl, int limit) {
        try {
            String rssContent = webClient.get()
                    .uri(rssUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            SyndFeed feed = parseRssContent(rssContent);

            return feed.getEntries().stream()
                    .limit(limit)
                    .map(this::convertToNewsItem)
                    .collect(Collectors.toList());

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

    public static class NewsItem {
        private final String title;
        private final String link;
        private final LocalDateTime publishedDate;

        public NewsItem(String title, String link, LocalDateTime publishedDate) {
            this.title = title;
            this.link = link;
            this.publishedDate = publishedDate;
        }

        public String getTitle() { return title; }
        public String getLink() { return link; }
        public LocalDateTime getPublishedDate() { return publishedDate; }

        @Override
        public String toString() {
            return String.format("- [%s](%s)", title, link);
        }
    }
}
