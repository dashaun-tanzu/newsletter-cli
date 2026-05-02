package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceTest {

    private DocumentService documentService;
    private Path testDir;
    private Path testFile;

    @BeforeEach
    void setup() throws IOException {
        documentService = new DocumentService();
        testDir = Files.createTempDirectory("newsletter-test");
        testFile = testDir.resolve("test-update.md");
    }

    @Test
    void shouldCreateNewDocument() throws IOException {
        documentService.createNewDocument(testFile.toString());

        assertTrue(Files.exists(testFile));
        String content = Files.readString(testFile);
        assertTrue(content.contains("# "));
        assertTrue(content.contains("## News:"));
        assertTrue(content.contains("## Recent Enterprise Releases:"));
        assertTrue(content.contains("## Releases coming soon:"));
        assertTrue(content.contains("## YouTube:"));
        assertTrue(content.contains("## Demos:"));
    }

    @Test
    void shouldCreateDocumentWithDefaultFilename() throws IOException {
        Path testDefaultFile = testDir.resolve("spring-update.md");
        documentService.createNewDocument(testDefaultFile.toString());

        assertTrue(Files.exists(testDefaultFile));
    }

    @Test
    void shouldUpdateNewsSection() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<RssService.NewsItem> newsItems = List.of(
            new RssService.NewsItem("Test News 1", "http://example.com/1", LocalDate.now().atStartOfDay()),
            new RssService.NewsItem("Test News 2", "http://example.com/2", LocalDate.now().atStartOfDay())
        );

        documentService.updateNewsSection(testFile.toString(), newsItems);

        String content = Files.readString(testFile);
        assertTrue(content.contains("[Test News 1](http://example.com/1)"));
        assertTrue(content.contains("[Test News 2](http://example.com/2)"));
    }

    @Test
    void shouldUpdateNewsSectionWhenDocumentNotExists() throws IOException {
        List<RssService.NewsItem> newsItems = List.of(
            new RssService.NewsItem("Test News", "http://example.com", LocalDate.now().atStartOfDay())
        );

        documentService.updateNewsSection(testFile.toString(), newsItems);

        assertTrue(Files.exists(testFile));
        String content = Files.readString(testFile);
        assertTrue(content.contains("[Test News](http://example.com)"));
    }

    @Test
    void shouldUpdateYouTubeSection() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<YouTubeService.YouTubeVideo> videos = List.of(
            new YouTubeService.YouTubeVideo("Test Video 1", "http://youtube.com/1", "Channel 1", LocalDate.now().atStartOfDay()),
            new YouTubeService.YouTubeVideo("Test Video 2", "http://youtube.com/2", "Channel 2", LocalDate.now().atStartOfDay())
        );

        documentService.updateYouTubeSection(testFile.toString(), videos);

        String content = Files.readString(testFile);
        assertTrue(content.contains("[Test Video 1](http://youtube.com/1) - Channel 1"));
        assertTrue(content.contains("[Test Video 2](http://youtube.com/2) - Channel 2"));
    }

    @Test
    void shouldUpdateYouTubeSectionWhenDocumentNotExists() throws IOException {
        List<YouTubeService.YouTubeVideo> videos = List.of(
            new YouTubeService.YouTubeVideo("Test Video", "http://youtube.com", "Channel", LocalDate.now().atStartOfDay())
        );

        documentService.updateYouTubeSection(testFile.toString(), videos);

        assertTrue(Files.exists(testFile));
        String content = Files.readString(testFile);
        assertTrue(content.contains("[Test Video](http://youtube.com) - Channel"));
    }

    @Test
    void shouldAddMultipleEnterpriseReleases() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<CalendarService.ReleaseEvent> releases = List.of(
            new CalendarService.ReleaseEvent("Spring Boot", "3.3.0", LocalDate.now(), "Spring Boot 3.3.0 (Enterprise)"),
            new CalendarService.ReleaseEvent("Spring Framework", "6.1.0", LocalDate.now().plusDays(1), "Spring Framework 6.1.0")
        );

        documentService.addMultipleEnterpriseReleases(testFile.toString(), releases);

        String content = Files.readString(testFile);
        assertTrue(content.contains("Spring Boot 3.3.0"));
        assertTrue(content.contains("Spring Framework 6.1.0"));
    }

    @Test
    void shouldUpdateReleasesComingSoon() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<CalendarService.ReleaseEvent> upcomingReleases = List.of(
            new CalendarService.ReleaseEvent("Spring Boot", "3.4.0", LocalDate.now().plusDays(5), "Spring Boot 3.4.0"),
            new CalendarService.ReleaseEvent("Spring Cloud", "2024.0.0", LocalDate.now().plusDays(10), "Spring Cloud 2024.0.0")
        );

        documentService.updateReleasesComingSoon(testFile.toString(), upcomingReleases);

        String content = Files.readString(testFile);
        assertTrue(content.contains("Spring Boot 3.4.0"));
        assertTrue(content.contains("Spring Cloud 2024.0.0"));
    }

    @Test
    void shouldAddDemo() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        documentService.updateDemo(testFile.toString(), "- [Demo App](https://github.com/example/demo)");

        String content = Files.readString(testFile);
        assertTrue(content.contains("[Demo App](https://github.com/example/demo)"));
    }

    @Test
    void shouldUpdateGitHubDemos() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<GitHubService.DemoRepository> demoRepos = List.of(
            new GitHubService.DemoRepository("test-demo", "A test demo", "https://github.com/example", LocalDateTime.now())
        );

        documentService.updateGitHubDemos(testFile.toString(), demoRepos);

        String content = Files.readString(testFile);
        assertTrue(content.contains("[test-demo](https://github.com/example/test-demo)"));
        assertTrue(content.contains("A test demo"));
    }

    @Test
    void shouldUpdateGitHubDemosWhenEmpty() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        documentService.updateGitHubDemos(testFile.toString(), List.of());

        String content = Files.readString(testFile);
        assertTrue(content.contains("No demo repositories found"));
    }

    @Test
    void shouldReadDocument() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        String content = documentService.readDocument(testFile.toString());

        assertTrue(content.contains("## News:"));
        assertTrue(content.contains("## Recent Enterprise Releases:"));
    }

    @Test
    void shouldReadNonExistentDocument() throws IOException {
        String result = documentService.readDocument(testFile.toString());

        assertTrue(result.contains("Document does not exist"));
    }

    @Test
    void shouldCleanUpMultipleBlankLines() throws IOException {
        documentService.createNewDocument(testFile.toString());
        
        List<CalendarService.ReleaseEvent> releases = List.of(
            new CalendarService.ReleaseEvent("Spring Boot", "3.3.0", LocalDate.now(), "Spring Boot 3.3.0"),
            new CalendarService.ReleaseEvent("Spring Framework", "6.1.0", LocalDate.now().plusDays(1), "Spring Framework 6.1.0"),
            new CalendarService.ReleaseEvent("Spring Security", "6.3.0", LocalDate.now().plusDays(2), "Spring Security 6.3.0")
        );

        documentService.addMultipleEnterpriseReleases(testFile.toString(), releases);

        String content = Files.readString(testFile);
        assertFalse(content.contains("\n\n\n"), "Should not have 3 or more consecutive blank lines");
    }

    @Test
    void shouldHandleNullFilename() throws IOException {
        Path testDefaultFile = testDir.resolve("spring-update.md");
        List<RssService.NewsItem> newsItems = List.of(
            new RssService.NewsItem("Test", "http://test.com", LocalDate.now().atStartOfDay())
        );

        documentService.updateNewsSection(testDefaultFile.toString(), newsItems);

        assertTrue(Files.exists(testDefaultFile));
    }

    @Test
    void shouldNotFilterArchivedRepositories() throws IOException {
        String jsonResponse = "[{\"name\":\"test-demo\",\"description\":\"Test\",\"html_url\":\"https://github.com/example\",\"updated_at\":\"2024-01-01T00:00:00Z\",\"archived\":true},"
                + "{\"name\":\"active-demo\",\"description\":\"Active\",\"html_url\":\"https://github.com/example\",\"updated_at\":\"2024-06-01T00:00:00Z\",\"archived\":false}]";
        
        GitHubService service = new GitHubService();
        
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);
        
        assertEquals(2, repos.size());
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("test-demo")));
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("active-demo")));
    }

    @Test
    void shouldExtractJsonFields() {
        GitHubService service = new GitHubService();
        
        String repoJson = "{\"name\":\"test\",\"description\":\"Test repo\",\"html_url\":\"https://example.com\",\"updated_at\":\"2024-01-01T00:00:00Z\"}";
        
        String name = service.extractJsonField(repoJson, "name");
        String desc = service.extractJsonField(repoJson, "description");
        String url = service.extractJsonField(repoJson, "html_url");
        String updated = service.extractJsonField(repoJson, "updated_at");
        
        assertEquals("test", name);
        assertEquals("Test repo", desc);
        assertEquals("https://example.com", url);
        assertEquals("2024-01-01T00:00:00Z", updated);
    }

    @Test
    void shouldHandleNullJsonFields() {
        GitHubService service = new GitHubService();
        
        String repoJson = "{\"name\":\"test\",\"description\":null}";
        
        String name = service.extractJsonField(repoJson, "name");
        String desc = service.extractJsonField(repoJson, "description");
        
        assertEquals("test", name);
        assertNull(desc);
    }
}
