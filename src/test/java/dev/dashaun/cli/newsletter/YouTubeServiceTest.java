package dev.dashaun.cli.newsletter;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
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
    void isShortShouldDetectShortsLink() {
        SyndEntry shortEntry = new SyndEntryImpl();
        shortEntry.setLink("https://www.youtube.com/shorts/mP1IV166bp8");
        assertTrue(YouTubeService.isShort(shortEntry));
    }

    @Test
    void isShortShouldNotFlagRegularVideo() {
        SyndEntry videoEntry = new SyndEntryImpl();
        videoEntry.setLink("https://www.youtube.com/watch?v=n2fPHV8741o");
        assertFalse(YouTubeService.isShort(videoEntry));
    }

    @Test
    void isShortShouldHandleNullLink() {
        SyndEntry entry = new SyndEntryImpl();
        // link is null by default
        assertFalse(YouTubeService.isShort(entry));
    }

    @Test
    void filterShouldExcludeShortsFromMixedList() {
        SyndEntry video1 = new SyndEntryImpl();
        video1.setLink("https://www.youtube.com/watch?v=AAA");
        video1.setTitle("Regular video 1");

        SyndEntry shortVid = new SyndEntryImpl();
        shortVid.setLink("https://www.youtube.com/shorts/SHORT1");
        shortVid.setTitle("Short clip");

        SyndEntry video2 = new SyndEntryImpl();
        video2.setLink("https://www.youtube.com/watch?v=BBB");
        video2.setTitle("Regular video 2");

        List<SyndEntry> kept = List.of(video1, shortVid, video2).stream()
                .filter(e -> !YouTubeService.isShort(e))
                .toList();

        assertEquals(2, kept.size());
        assertEquals("Regular video 1", kept.get(0).getTitle());
        assertEquals("Regular video 2", kept.get(1).getTitle());
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
