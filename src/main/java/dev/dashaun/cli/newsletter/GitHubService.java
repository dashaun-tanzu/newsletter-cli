package dev.dashaun.cli.newsletter;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GitHubService {

    private final RestTemplate restTemplate;
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String ORG_NAME = "dashaun-tanzu";

    public GitHubService() {
        this.restTemplate = new RestTemplate();
    }

    public List<DemoRepository> fetchDemoRepositories() {
        try {
            String apiUrl = String.format("%s/orgs/%s/repos?type=public&per_page=100", GITHUB_API_BASE, ORG_NAME);

            // Fetch as plain string to avoid JSON parsing issues
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return List.of();
            }

            List<DemoRepository> repositories = parseRepositories(jsonResponse);

            return repositories.stream()
                    .filter(repo -> repo.getName() != null && repo.getName().endsWith("-demo"))
                    .filter(repo -> !isArchived(jsonResponse, repo.getName()))
                    .sorted((a, b) -> {
                        if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                        if (a.getUpdatedAt() == null) return 1;
                        if (b.getUpdatedAt() == null) return -1;
                        return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitHub repositories: " + e.getMessage(), e);
        }
    }

    private List<DemoRepository> parseRepositories(String jsonResponse) {
        List<DemoRepository> repositories = new ArrayList<>();

        // Find all repository objects in the JSON array
        Pattern repoPattern = Pattern.compile("\\{[^}]*\"name\"[^}]*\\}");
        Matcher repoMatcher = repoPattern.matcher(jsonResponse);

        while (repoMatcher.find()) {
            String repoJson = repoMatcher.group();

            String name = extractJsonField(repoJson, "name");
            String description = extractJsonField(repoJson, "description");
            String htmlUrl = extractJsonField(repoJson, "html_url");
            String updatedAtStr = extractJsonField(repoJson, "updated_at");

            LocalDateTime updatedAt = null;
            if (updatedAtStr != null && !updatedAtStr.equals("null")) {
                try {
                    updatedAt = LocalDateTime.parse(updatedAtStr, DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }

            if (name != null) {
                repositories.add(new DemoRepository(
                        name,
                        description != null && !description.equals("null") ? description : "Demo repository",
                        htmlUrl,
                        updatedAt
                ));
            }
        }

        return repositories;
    }

    private String extractJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"|\"" + fieldName + "\"\\s*:\\s*(null)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : null;
        }
        return null;
    }

    private boolean isArchived(String jsonResponse, String repoName) {
        // Look for the archived field in the same object that contains this repo name
        String repoSection = extractRepoSection(jsonResponse, repoName);
        if (repoSection != null) {
            Pattern archivedPattern = Pattern.compile("\"archived\"\\s*:\\s*(true|false)");
            Matcher matcher = archivedPattern.matcher(repoSection);
            if (matcher.find()) {
                return Boolean.parseBoolean(matcher.group(1));
            }
        }
        return false;
    }

    private String extractRepoSection(String jsonResponse, String repoName) {
        // Find the section of JSON that contains this repo
        Pattern pattern = Pattern.compile("\\{[^{}]*\"name\"\\s*:\\s*\"" + Pattern.quote(repoName) + "\"[^{}]*\\}");
        Matcher matcher = pattern.matcher(jsonResponse);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public static class DemoRepository {
        private final String name;
        private final String description;
        private final String url;
        private final LocalDateTime updatedAt;

        public DemoRepository(String name, String description, String url, LocalDateTime updatedAt) {
            this.name = name;
            this.description = description;
            this.url = url + "/" + name;
            this.updatedAt = updatedAt;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getUrl() { return url; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }

        @Override
        public String toString() {
            return String.format("- [%s](%s) - %s", name, url, description);
        }
    }
}