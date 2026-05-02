package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;

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
