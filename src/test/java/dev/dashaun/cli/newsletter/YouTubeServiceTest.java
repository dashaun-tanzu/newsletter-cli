package dev.dashaun.cli.newsletter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
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

    @Nested
    class FeedFailures {

        private static final String ATOM_FEED = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Test Channel</title>
                  <entry>
                    <id>yt:video:AAA</id>
                    <title>A Real Video</title>
                    <link rel="alternate" href="https://www.youtube.com/watch?v=AAA"/>
                    <published>2026-08-01T12:00:00+00:00</published>
                    <updated>2026-08-01T12:00:00+00:00</updated>
                  </entry>
                </feed>
                """;

        private WireMockServer wireMock;

        @BeforeEach
        void startServer() {
            wireMock = new WireMockServer(options().dynamicPort());
            wireMock.start();
        }

        @AfterEach
        void stopServer() {
            wireMock.stop();
        }

        /** Retries are shortened so the test does not sit through the production 75s-per-channel budget. */
        private YouTubeService serviceUnderTest() {
            return new YouTubeService(wireMock.baseUrl() + "/feeds/videos.xml", 2, Duration.ofMillis(1));
        }

        @Test
        void shouldThrowWhenEveryChannelFeedFails() {
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .willReturn(aResponse().withStatus(404)));

            YouTubeService service = serviceUnderTest();

            YouTubeService.YouTubeUnavailableException thrown = assertThrows(
                    YouTubeService.YouTubeUnavailableException.class,
                    () -> service.fetchLatestVideos(10));

            // The whole point: an outage must not be reported as "0 videos".
            assertTrue(thrown.getMessage().contains("all 3 channel feeds failed"),
                    "unexpected message: " + thrown.getMessage());
        }

        @Test
        void shouldReturnVideosWhenOnlySomeChannelsFail() {
            // Coffee + Software succeeds; the other two 404 forever.
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .willReturn(aResponse().withStatus(404)));
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .withQueryParam("channel_id", equalTo("UCjcceQmjS4DKBW_J_1UANow"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(ATOM_FEED)));

            List<YouTubeService.YouTubeVideo> videos = serviceUnderTest().fetchLatestVideos(10);

            assertEquals(1, videos.size());
            assertEquals("A Real Video", videos.get(0).getTitle());
            assertEquals("Coffee + Software", videos.get(0).getChannelName());
        }

        @Test
        void shouldRetryTransient404ThenSucceed() {
            String scenario = "flaky feed";
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml")).inScenario(scenario)
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(404))
                    .willSetStateTo("recovered"));
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml")).inScenario(scenario)
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(ATOM_FEED)));

            List<YouTubeService.YouTubeVideo> videos = serviceUnderTest().fetchLatestVideos(10);

            assertFalse(videos.isEmpty(), "a transient 404 should be retried, not given up on");
        }

        @Test
        void shouldSendIdentifiableUserAgent() {
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(ATOM_FEED)));

            serviceUnderTest().fetchLatestVideos(10);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/feeds/videos.xml"))
                    .withHeader("User-Agent", containing("newsletter-cli")));
        }
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
