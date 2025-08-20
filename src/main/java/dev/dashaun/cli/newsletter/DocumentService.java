package dev.dashaun.cli.newsletter;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentService {

    private static final String DEFAULT_FILENAME = "spring-update.md";
    private static final Pattern NEWS_SECTION_PATTERN = Pattern.compile(
            "(## News:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)",
            Pattern.DOTALL
    );
    private static final Pattern YOUTUBE_SECTION_PATTERN = Pattern.compile(
            "(## YouTube:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)",
            Pattern.DOTALL
    );
    private static final Pattern ENTERPRISE_RELEASES_SECTION_PATTERN = Pattern.compile(
            "(## Recent Enterprise Releases:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)",
            Pattern.DOTALL);
    private static final Pattern RELEASES_COMING_SOON_SECTION_PATTERN = Pattern.compile(
            "(## Releases coming soon:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)",
            Pattern.DOTALL);


    public void createNewDocument(String filename) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d"));

        String template = String.format("""
                # %s
                
                ## News:
                
                ## Recent Enterprise Releases:
                
                ## Releases coming soon:
                
                ## YouTube:
                
                ## Demos:
                
                
                """, date);

        Files.writeString(path, template, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void updateNewsSection(String filename, List<RssService.NewsItem> newsItems) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        String newsSection = buildNewsSection(newsItems);

        Matcher matcher = NEWS_SECTION_PATTERN.matcher(content);
        if (matcher.find()) {
            String updatedContent = content.substring(0, matcher.start(2)) +
                    newsSection +
                    content.substring(matcher.end(2));
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            // If no news section found, add it after the date
            String datePattern = "^# (.+)$";
            Pattern pattern = Pattern.compile(datePattern, Pattern.MULTILINE);
            Matcher dateMatcher = pattern.matcher(content);

            if (dateMatcher.find()) {
                String updatedContent = content.substring(0, dateMatcher.end()) +
                        "\n\n## News:\n\n" + newsSection +
                        content.substring(dateMatcher.end());
                Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    private String buildNewsSection(List<RssService.NewsItem> newsItems) {
        StringBuilder sb = new StringBuilder();
        for (RssService.NewsItem item : newsItems) {
            sb.append(item.toString()).append("\n");
        }
        return sb.toString();
    }

    public void updateYouTubeSection(String filename, List<YouTubeService.YouTubeVideo> videos) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        String youtubeSection = buildYouTubeSection(videos);

        Matcher matcher = YOUTUBE_SECTION_PATTERN.matcher(content);
        if (matcher.find()) {
            String updatedContent = content.substring(0, matcher.start(2)) +
                    youtubeSection +
                    content.substring(matcher.end(2));
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            // If no YouTube section found, add it before the Demos section
            Pattern demosPattern = Pattern.compile("(## Demos:\\s*\\n)", Pattern.DOTALL);
            Matcher demosMatcher = demosPattern.matcher(content);
            
            if (demosMatcher.find()) {
                String updatedContent = content.substring(0, demosMatcher.start()) +
                        "## YouTube:\n\n" + youtubeSection + "\n" +
                        content.substring(demosMatcher.start());
                Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                // If no demos section, add at the end
                String updatedContent = content.trim() + "\n\n## YouTube:\n\n" + youtubeSection + "\n";
                Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    private String buildYouTubeSection(List<YouTubeService.YouTubeVideo> videos) {
        StringBuilder sb = new StringBuilder();
        for (YouTubeService.YouTubeVideo video : videos) {
            sb.append(video.toString()).append("\n");
        }
        return sb.toString();
    }

    public String readDocument(String filename) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);
        if (!Files.exists(path)) {
            return "Document does not exist: " + path.toAbsolutePath();
        }
        return Files.readString(path);
    }

    public void addMultipleEnterpriseReleases(String filename, List<CalendarService.ReleaseEvent> releases) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Matcher matcher = ENTERPRISE_RELEASES_SECTION_PATTERN.matcher(content);

        StringBuilder newReleases = new StringBuilder();
        // Group releases by date, maintaining chronological order
        releases.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getReleaseDate().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d")),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .forEach((date, releaseList) -> {
                    newReleases.append("- ").append(date).append("\n");
                    releaseList.forEach(release ->
                            newReleases.append("  - ").append(release.getFormattedRelease()).append("\n"));
                });

        if (matcher.find()) {
            // Section exists, update it
            String existingReleases = matcher.group(2);
            String updatedReleases = newReleases.toString() + existingReleases;

            String updatedContent = content.substring(0, matcher.start(2)) +
                    updatedReleases +
                    content.substring(matcher.end(2));
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            // Section doesn't exist, create it after the News section
            Matcher newsMatcher = NEWS_SECTION_PATTERN.matcher(content);
            
            if (newsMatcher.find()) {
                String updatedContent = content.substring(0, newsMatcher.end()) +
                        "\n## Recent Enterprise Releases:\n\n" + newReleases +
                        content.substring(newsMatcher.end());
                Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    public void updateReleasesComingSoon(String filename, List<CalendarService.ReleaseEvent> upcomingReleases) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Matcher matcher = RELEASES_COMING_SOON_SECTION_PATTERN.matcher(content);

        if (matcher.find()) {
            StringBuilder upcomingSection = new StringBuilder();

            // Add releases from calendar
            upcomingReleases.forEach(release ->
                    upcomingSection.append("- ")
                            .append(release.getFormattedRelease())
                            .append(" (")
                            .append(release.getReleaseDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
                            .append(")\n"));

            String updatedContent = content.substring(0, matcher.start(2)) +
                    upcomingSection +
                    content.substring(matcher.end(2));
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public void updateDemo(String filename, String demoDescription) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Pattern pattern = Pattern.compile("(## Demos:\\s*\\n)(.*?)$", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String updatedContent = content.substring(0, matcher.start(2)) +
                    "\n" + demoDescription + "\n\n";
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public void updateGitHubDemos(String filename, List<GitHubService.DemoRepository> demoRepos) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Pattern pattern = Pattern.compile("(## Demos:\\s*\\n)(.*?)$", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        StringBuilder demosSection = new StringBuilder();
        if (!demoRepos.isEmpty()) {
            demosSection.append("\n");
            for (GitHubService.DemoRepository repo : demoRepos) {
                demosSection.append(repo.toString()).append("\n");
            }
            demosSection.append("\n");
        } else {
            demosSection.append("\nNo demo repositories found.\n\n");
        }

        if (matcher.find()) {
            String updatedContent = content.substring(0, matcher.start(2)) +
                    demosSection +
                    content.substring(matcher.end());
            writeDocumentWithCleanup(path, updatedContent);
        }
    }

    private String removeDoubleSpacing(String content) {
        // Replace multiple consecutive blank lines with single blank lines
        return content.replaceAll("\\n\\n\\n+", "\n\n");
    }

    private void writeDocumentWithCleanup(Path path, String content) throws IOException {
        String cleanedContent = removeDoubleSpacing(content);
        Files.writeString(path, cleanedContent, StandardOpenOption.TRUNCATE_EXISTING);
    }
}