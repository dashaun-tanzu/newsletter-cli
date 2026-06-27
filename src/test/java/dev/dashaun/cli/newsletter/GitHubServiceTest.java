package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubServiceTest {

    @Test
    void shouldParseRepository() {
        String jsonResponse = "[{\"name\":\"test-demo\",\"description\":\"Test description\",\"html_url\":\"https://github.com/example\",\"updated_at\":\"2024-01-15T10:00:00Z\",\"archived\":false}]";
        
        GitHubService service = new GitHubService();
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);
        
        assertEquals(1, repos.size());
        assertEquals("test-demo", repos.get(0).getName());
        assertEquals("Test description", repos.get(0).getDescription());
        assertEquals("https://github.com/example/test-demo", repos.get(0).getUrl());
    }

    @Test
    void shouldExtractRepositoryFields() {
        String repoJson = "{\"name\":\"spring-demo\",\"description\":\"Spring demo app\",\"html_url\":\"https://github.com/dashaun-tanzu\",\"updated_at\":\"2024-06-01T00:00:00Z\"}";
        
        GitHubService service = new GitHubService();
        
        String name = service.extractJsonField(repoJson, "name");
        String desc = service.extractJsonField(repoJson, "description");
        String url = service.extractJsonField(repoJson, "html_url");
        
        assertEquals("spring-demo", name);
        assertEquals("Spring demo app", desc);
        assertEquals("https://github.com/dashaun-tanzu", url);
    }

    @Test
    void shouldHandleNullFields() {
        String repoJson = "{\"name\":\"test\",\"description\":null,\"html_url\":null,\"updated_at\":null}";
        
        GitHubService service = new GitHubService();
        
        String name = service.extractJsonField(repoJson, "name");
        String desc = service.extractJsonField(repoJson, "description");
        String url = service.extractJsonField(repoJson, "html_url");
        String updated = service.extractJsonField(repoJson, "updated_at");
        
        assertEquals("test", name);
        assertNull(desc);
        assertNull(url);
        assertNull(updated);
    }

    @Test
    void shouldFilterArchivedRepositories() {
        String jsonResponse = "[{\"name\":\"active-demo\",\"description\":\"Active\",\"html_url\":\"https://github.com/example\",\"archived\":false},"
                + "{\"name\":\"archived-demo\",\"description\":\"Old\",\"html_url\":\"https://github.com/example\",\"archived\":true}]";
        
        GitHubService service = new GitHubService();
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);
        
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("active-demo")));
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("archived-demo")));
    }

    @Test
    void shouldHandleEmptyResponse() {
        GitHubService service = new GitHubService();
        
        List<GitHubService.DemoRepository> repos = service.parseRepositories("[]");
        
        assertTrue(repos.isEmpty());
    }

    @Test
    void shouldParseMultipleRepositories() {
        String jsonResponse = "[{\"name\":\"demo1\",\"description\":\"First\",\"html_url\":\"https://github.com/example\",\"archived\":false},"
                + "{\"name\":\"demo2\",\"description\":\"Second\",\"html_url\":\"https://github.com/example\",\"archived\":false}]";
        
        GitHubService service = new GitHubService();
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);
        
        assertEquals(2, repos.size());
        assertEquals("demo1", repos.get(0).getName());
        assertEquals("demo2", repos.get(1).getName());
    }

    @Test
    void shouldGetDemoRepositoryUrl() {
        GitHubService.DemoRepository repo = new GitHubService.DemoRepository(
            "spring-demo",
            "Spring demo",
            "https://github.com/dashaun-tanzu",
            LocalDateTime.now()
        );
        
        assertEquals("https://github.com/dashaun-tanzu/spring-demo", repo.getUrl());
    }

    @Test
    void shouldGetDemoRepositoryDescription() {
        String description = "A Spring Boot demo application";
        GitHubService.DemoRepository repo = new GitHubService.DemoRepository(
            "test-demo",
            description,
            "https://github.com/example",
            LocalDateTime.now()
        );
        
        assertEquals(description, repo.getDescription());
    }

    @Test
    void shouldGetDemoRepositoryUpdatedAt() {
        LocalDateTime updatedAt = LocalDateTime.now();
        GitHubService.DemoRepository repo = new GitHubService.DemoRepository(
            "test-demo",
            "Test",
            "https://github.com/example",
            updatedAt
        );
        
        assertEquals(updatedAt, repo.getUpdatedAt());
    }

    @Test
    void shouldFormatDemoRepository() {
        GitHubService.DemoRepository repo = new GitHubService.DemoRepository(
            "spring-demo",
            "Spring Boot demo",
            "https://github.com/example",
            LocalDateTime.now()
        );
        
        String formatted = repo.toString();
        
        assertTrue(formatted.contains("[spring-demo](https://github.com/example/spring-demo)"));
        assertTrue(formatted.contains("Spring Boot demo"));
    }

    @Test
    void shouldHandleNullUpdatedAt() {
        GitHubService.DemoRepository repo = new GitHubService.DemoRepository(
            "test-demo",
            "Test",
            "https://github.com/example",
            null
        );
        
        assertNull(repo.getUpdatedAt());
    }

    @Test
    void shouldFilterByDemoSuffix() {
        String jsonResponse = "[{\"name\":\"spring-app\",\"description\":\"App\",\"html_url\":\"https://github.com/example\",\"archived\":false},"
                + "{\"name\":\"spring-demo\",\"description\":\"Demo\",\"html_url\":\"https://github.com/example\",\"archived\":false},"
                + "{\"name\":\"web-demo\",\"description\":\"Web Demo\",\"html_url\":\"https://github.com/example\",\"archived\":false}]";
        
        GitHubService service = new GitHubService();
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);
        
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("spring-demo")));
        assertTrue(repos.stream().anyMatch(r -> r.getName().equals("web-demo")));
    }

    @Test
    void shouldExtractRepoSectionFromJson() {
        String jsonResponse = "[{\"name\":\"test-demo\",\"description\":\"Test\",\"archived\":false}]";
        
        GitHubService service = new GitHubService();
        String section = service.extractRepoSection(jsonResponse, "test-demo");
        
        assertNotNull(section);
        assertTrue(section.contains("test-demo"));
        assertTrue(section.contains("\"archived\":false"));
    }

    @Test
    void shouldUseDescriptionWhenRepoHasNestedObjects() {
        // Mirrors the real GitHub API shape: the "owner" object precedes "description".
        String jsonResponse = "[{\"name\":\"saa-petclinic-demo\","
                + "\"owner\":{\"login\":\"dashaun-tanzu\",\"html_url\":\"https://github.com/dashaun-tanzu\"},"
                + "\"html_url\":\"https://github.com/dashaun-tanzu/saa-petclinic-demo\","
                + "\"description\":\"PetClinic on Spring Boot\","
                + "\"updated_at\":\"2024-06-01T00:00:00Z\",\"archived\":false}]";

        GitHubService service = new GitHubService();
        List<GitHubService.DemoRepository> repos = service.parseRepositories(jsonResponse);

        assertEquals(1, repos.size());
        assertEquals("saa-petclinic-demo", repos.get(0).getName());
        assertEquals("PetClinic on Spring Boot", repos.get(0).getDescription());
        assertEquals("https://github.com/dashaun-tanzu/saa-petclinic-demo", repos.get(0).getUrl());
    }

    @Test
    void shouldDetectArchivedWhenRepoHasNestedObjects() {
        // Mirrors the real GitHub API shape: nested "owner" object before "archived".
        String jsonResponse = "[{\"name\":\"archived-demo\","
                + "\"owner\":{\"login\":\"dashaun-tanzu\",\"html_url\":\"https://github.com/dashaun-tanzu\"},"
                + "\"description\":\"Old\",\"archived\":true},"
                + "{\"name\":\"active-demo\","
                + "\"owner\":{\"login\":\"dashaun-tanzu\",\"html_url\":\"https://github.com/dashaun-tanzu\"},"
                + "\"description\":\"New\",\"archived\":false}]";

        GitHubService service = new GitHubService();

        assertTrue(service.isArchived(jsonResponse, "archived-demo"));
        assertFalse(service.isArchived(jsonResponse, "active-demo"));
    }

    @Test
    void shouldHandleNullJsonResponse() {
        GitHubService service = new GitHubService();
        
        List<GitHubService.DemoRepository> repos = service.parseRepositories(null);
        
        assertTrue(repos.isEmpty());
    }

    @Test
    void shouldHandleEmptyJsonResponse() {
        GitHubService service = new GitHubService();
        
        List<GitHubService.DemoRepository> repos = service.parseRepositories("");
        
        assertTrue(repos.isEmpty());
    }
}
