package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RssServiceTest {

    @Test
    void shouldParseNewsItem() {
        RssService service = new RssService();
        
        RssService.NewsItem item = new RssService.NewsItem(
            "Test Title",
            "http://example.com/test",
            LocalDate.now().atStartOfDay()
        );
        
        assertTrue(item.title().contains("Test"));
        assertTrue(item.link().contains("example.com"));
        assertNotNull(item.publishedDate());
    }

    @Test
    void shouldFormatNewsItem() {
        RssService.NewsItem item = new RssService.NewsItem(
            "Test Title",
            "http://example.com/test",
            LocalDate.now().atStartOfDay()
        );
        
        String formatted = item.toString();
        
        assertTrue(formatted.contains("[Test Title](http://example.com/test)"));
    }

    @Test
    void shouldReturnEmptyListWhenLimitIsZero() {
        RssService service = new RssService();
        
        List<RssService.NewsItem> items = service.fetchLatestNews("https://spring.io/blog/category/releases.atom", 0);
        
        assertTrue(items.isEmpty());
    }

    @Test
    void shouldLimitResults() {
        RssService service = new RssService();
        
        List<RssService.NewsItem> items = service.fetchLatestNews("https://spring.io/blog/category/releases.atom", 3);
        
        assertTrue(items.size() <= 3);
    }

    @Test
    void shouldIncludeLinkInSection() {
        RssService.NewsItem item = new RssService.NewsItem(
            "Spring Boot 3.3.0 Released",
            "https://spring.io/blog/2024/01/01/spring-boot-3-3-0-released",
            LocalDate.now().atStartOfDay()
        );
        
        String formatted = item.toString();
        
        assertTrue(formatted.startsWith("- ["));
        assertTrue(formatted.contains("]("));
        assertTrue(formatted.endsWith(")"));
    }

    @Test
    void shouldHandleNullDate() {
        RssService.NewsItem item = new RssService.NewsItem(
            "Test",
            "http://example.com",
            null
        );
        
        assertNotNull(item);
        assertNull(item.publishedDate());
    }
}
