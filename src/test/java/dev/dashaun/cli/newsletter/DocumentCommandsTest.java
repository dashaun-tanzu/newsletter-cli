package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers what happens to the document when the YouTube feeds are unavailable. The failure mode
 * this guards against: reporting success while writing an empty section, which the newsletter
 * repo then published.
 */
class DocumentCommandsTest {

    private static final String EXISTING_DOC = """
            # August 13

            ## News:
            - [Some news](https://example.com/news)

            ## YouTube:

            - [Yesterday's video](https://youtube.com/watch?v=OLD) - Coffee + Software

            ## Demos:
            - [a-demo](https://github.com/example/a-demo) - description
            """;

    private ExitCodeTracker exitCodeTracker;
    private DocumentService documentService;
    private Path testFile;

    @BeforeEach
    void setup() throws IOException {
        exitCodeTracker = new ExitCodeTracker();
        documentService = new DocumentService();
        testFile = Files.createTempDirectory("newsletter-commands-test").resolve("update.md");
        Files.writeString(testFile, EXISTING_DOC);
    }

    private DocumentCommands commandsWith(YouTubeService youTubeService) {
        return new DocumentCommands(null, documentService, null, youTubeService, null, exitCodeTracker);
    }

    /** Stands in for a total outage: every channel exhausted its retries. */
    private static YouTubeService unavailableService() {
        return new YouTubeService() {
            @Override
            public List<YouTubeVideo> fetchLatestVideos(int limit) {
                throw new YouTubeUnavailableException("all 3 channel feeds failed after retries (a, b, c)");
            }
        };
    }

    @Test
    void shouldLeaveExistingVideosInPlaceWhenYouTubeIsUnavailable() throws IOException {
        String message = commandsWith(unavailableService()).updateYouTube(testFile.toString(), 10);

        assertTrue(message.contains("left unchanged"), "unexpected message: " + message);
        assertTrue(Files.readString(testFile).contains("Yesterday's video"),
                "the previous video list must survive an outage");
    }

    @Test
    void shouldReportNonZeroExitCodeWhenYouTubeIsUnavailable() {
        assertEquals(0, exitCodeTracker.getExitCode(), "precondition: nothing has failed yet");

        commandsWith(unavailableService()).updateYouTube(testFile.toString(), 10);

        assertEquals(1, exitCodeTracker.getExitCode(),
                "an outage must surface as a non-zero exit so the caller can retry");
    }

    @Test
    void shouldExitZeroAndReplaceSectionOnSuccess() throws IOException {
        YouTubeService working = new YouTubeService() {
            @Override
            public List<YouTubeVideo> fetchLatestVideos(int limit) {
                return List.of(new YouTubeVideo("A Brand New Video",
                        "https://youtube.com/watch?v=NEW", "Dan Vega", null));
            }
        };

        String message = commandsWith(working).updateYouTube(testFile.toString(), 10);

        assertTrue(message.contains("Updated YouTube section with 1 videos"), "unexpected message: " + message);
        assertEquals(0, exitCodeTracker.getExitCode());

        String content = Files.readString(testFile);
        assertTrue(content.contains("A Brand New Video"));
        assertFalse(content.contains("Yesterday's video"), "a successful fetch replaces the old list");
    }
}
