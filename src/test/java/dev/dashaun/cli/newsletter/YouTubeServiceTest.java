package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeServiceTest {

    @Test
    void shouldParseYouTubeVideo() {
        YouTubeService.YouTubeVideo video = new YouTubeService.YouTubeVideo(
            "Spring Boot Tutorial",
            "https://youtube.com/watch?v=test123",
            "Coffee + Software",
            LocalDate.now().atStartOfDay()
        );
        
        assertEquals("Spring Boot Tutorial", video.getTitle());
        assertEquals("https://youtube.com/watch?v=test123", video.getLink());
        assertEquals("Coffee + Software", video.getChannelName());
        assertNotNull(video.getPublishedDate());
    }

    @Test
    void shouldFormatYouTubeVideo() {
        YouTubeService.YouTubeVideo video = new YouTubeService.YouTubeVideo(
            "Spring Boot Tutorial",
            "https://youtube.com/watch?v=test123",
            "Coffee + Software",
            LocalDate.now().atStartOfDay()
        );
        
        String formatted = video.toString();
        
        assertTrue(formatted.contains("[Spring Boot Tutorial](https://youtube.com/watch?v=test123)"));
        assertTrue(formatted.contains("Coffee + Software"));
    }

    @Test
    void shouldReturnEmptyListWhenLimitIsZero() {
        YouTubeService service = new YouTubeService();
        
        List<YouTubeService.YouTubeVideo> videos = service.fetchLatestVideos(0);
        
        assertTrue(videos.isEmpty());
    }

    @Test
    void shouldHandleNullPublishedDate() {
        YouTubeService.YouTubeVideo video = new YouTubeService.YouTubeVideo(
            "Test",
            "http://youtube.com/test",
            "Channel",
            null
        );
        
        assertNotNull(video);
        assertNull(video.getPublishedDate());
    }

    @Test
    void retryPredicateShouldRetryOn404() {
        WebClientResponseException notFound =
                WebClientResponseException.create(404, "Not Found", null, null, null);
        assertTrue(YouTubeService.RETRY_PREDICATE.test(notFound));
    }

    @Test
    void retryPredicateShouldRetryOn404WrappedInCauseChain() {
        WebClientResponseException notFound =
                WebClientResponseException.create(404, "Not Found", null, null, null);
        RuntimeException wrapped = new RuntimeException("upstream failure",
                new RuntimeException("inner", notFound));
        assertTrue(YouTubeService.RETRY_PREDICATE.test(wrapped));
    }

    @Test
    void retryPredicateShouldDelegateToDefaultForRetryableTypes() {
        assertTrue(YouTubeService.RETRY_PREDICATE.test(new IOException("boom")));
        assertTrue(YouTubeService.RETRY_PREDICATE.test(
                WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
    }

    @Test
    void retryPredicateShouldNotRetryOn403() {
        WebClientResponseException forbidden =
                WebClientResponseException.create(403, "Forbidden", null, null, null);
        assertFalse(YouTubeService.RETRY_PREDICATE.test(forbidden));
    }

    @Test
    void shouldFormatYouTubeVideoWithChannelName() {
        YouTubeService.YouTubeVideo video = new YouTubeService.YouTubeVideo(
            "Test Video",
            "http://youtube.com/test",
            "Test Channel",
            LocalDate.now().atStartOfDay()
        );
        
        String formatted = video.toString();
        
        assertTrue(formatted.contains("Test Channel"));
    }
}
