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

        /** Two entries per channel, so a limit can be filled beyond the one reserved slot. */
        private static String feedFor(String channel, String recentDate, String olderDate) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <feed xmlns="http://www.w3.org/2005/Atom">
                      <title>%1$s</title>
                      <entry>
                        <id>yt:video:%1$s-1</id>
                        <title>%1$s recent</title>
                        <link rel="alternate" href="https://www.youtube.com/watch?v=%1$s1"/>
                        <published>%2$s</published>
                        <updated>%2$s</updated>
                      </entry>
                      <entry>
                        <id>yt:video:%1$s-2</id>
                        <title>%1$s older</title>
                        <link rel="alternate" href="https://www.youtube.com/watch?v=%1$s2"/>
                        <published>%3$s</published>
                        <updated>%3$s</updated>
                      </entry>
                    </feed>
                    """.formatted(channel, recentDate, olderDate);
        }

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
            return serviceUnderTest(3);
        }

        private YouTubeService serviceUnderTest(int channelSweeps) {
            return new YouTubeService(wireMock.baseUrl() + "/feeds/videos.xml", 2, Duration.ofMillis(1),
                    channelSweeps, Duration.ofMillis(1));
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

            YouTubeService.FetchResult result = serviceUnderTest().fetchLatest(10);

            assertEquals(1, result.videos().size());
            assertEquals("A Real Video", result.videos().get(0).getTitle());
            assertEquals("Coffee + Software", result.videos().get(0).getChannelName());
            // The two dead channels must be named, not silently dropped.
            assertFalse(result.isComplete());
            assertEquals(List.of("SpringSourceDev", "Dan Vega"), result.missingChannels());
        }

        @Test
        void shouldRecoverAChannelOnALaterSweep() {
            // Per-request retries (2 attempts) are exhausted by the first sweep; only the
            // channel-level sweep gets this feed back.
            String scenario = "slow recovery";
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml")).inScenario(scenario)
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(404))
                    .willSetStateTo("second"));
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml")).inScenario(scenario)
                    .whenScenarioStateIs("second")
                    .willReturn(aResponse().withStatus(404))
                    .willSetStateTo("recovered"));
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml")).inScenario(scenario)
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(ATOM_FEED)));

            YouTubeService.FetchResult result = serviceUnderTest().fetchLatest(10);

            assertTrue(result.isComplete(), "every channel should recover across sweeps");
            assertEquals(3, result.videos().size());
        }

        @Test
        void shouldTreatAFeedWithNoUsableVideosAsAMissingChannel() {
            String shortsOnly = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <feed xmlns="http://www.w3.org/2005/Atom">
                      <title>Shorts Only</title>
                      <entry>
                        <id>yt:video:SHORT</id>
                        <title>A Short</title>
                        <link rel="alternate" href="https://www.youtube.com/shorts/SHORT"/>
                        <published>2026-08-01T12:00:00+00:00</published>
                        <updated>2026-08-01T12:00:00+00:00</updated>
                      </entry>
                    </feed>
                    """;
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(ATOM_FEED)));
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .withQueryParam("channel_id", equalTo("UCc98QQw1D-y38wg6mO3w4MQ"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(shortsOnly)));

            YouTubeService.FetchResult result = serviceUnderTest().fetchLatest(10);

            // A 200 that yields nothing publishable leaves the channel out of the newsletter
            // just as surely as a 404 does.
            assertEquals(List.of("Dan Vega"), result.missingChannels());
            assertEquals(2, result.videos().size());
        }

        @Test
        void shouldIncludeAVideoFromEveryChannelEvenWhenOneChannelIsNewest() {
            // Coffee + Software holds the two most recent videos overall; a plain
            // "latest 3 by date" would hand it two slots and drop a channel entirely.
            stubChannel("UCjcceQmjS4DKBW_J_1UANow", feedFor("coffee",
                    "2026-08-10T12:00:00+00:00", "2026-08-09T12:00:00+00:00"));
            stubChannel("UC7yfnfvEUlXUIfm8rGLwZdA", feedFor("spring",
                    "2026-07-20T12:00:00+00:00", "2026-07-10T12:00:00+00:00"));
            stubChannel("UCc98QQw1D-y38wg6mO3w4MQ", feedFor("danvega",
                    "2026-07-15T12:00:00+00:00", "2026-07-05T12:00:00+00:00"));

            YouTubeService.FetchResult result = serviceUnderTest().fetchLatest(3);

            assertTrue(result.isComplete());
            assertEquals(3, result.videos().size());
            assertEquals(List.of("Coffee + Software", "SpringSourceDev", "Dan Vega"),
                    channelNamesInOrder(result));
        }

        @Test
        void shouldFillRemainingSlotsByRecencyAfterEveryChannelIsRepresented() {
            stubChannel("UCjcceQmjS4DKBW_J_1UANow", feedFor("coffee",
                    "2026-08-10T12:00:00+00:00", "2026-08-09T12:00:00+00:00"));
            stubChannel("UC7yfnfvEUlXUIfm8rGLwZdA", feedFor("spring",
                    "2026-07-20T12:00:00+00:00", "2026-07-10T12:00:00+00:00"));
            stubChannel("UCc98QQw1D-y38wg6mO3w4MQ", feedFor("danvega",
                    "2026-07-15T12:00:00+00:00", "2026-07-05T12:00:00+00:00"));

            List<YouTubeService.YouTubeVideo> videos = serviceUnderTest().fetchLatest(4).videos();

            assertEquals(4, videos.size());
            // One reserved pick per channel, then the newest leftover (coffee's second video).
            assertEquals(List.of("coffee recent", "coffee older", "spring recent", "danvega recent"),
                    videos.stream().map(YouTubeService.YouTubeVideo::getTitle).toList());
        }

        private void stubChannel(String channelId, String body) {
            wireMock.stubFor(get(urlPathEqualTo("/feeds/videos.xml"))
                    .withQueryParam("channel_id", equalTo(channelId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/atom+xml")
                            .withBody(body)));
        }

        private List<String> channelNamesInOrder(YouTubeService.FetchResult result) {
            return result.videos().stream()
                    .map(YouTubeService.YouTubeVideo::getChannelName)
                    .toList();
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
