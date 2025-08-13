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

    public void createNewDocument(String filename) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d"));

        String template = String.format("""
                # %s
                
                ## News:
                
                ## Recent Enterprise Releases:
                
                ## Releases coming soon:
                
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

    public String readDocument(String filename) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);
        if (!Files.exists(path)) {
            return "Document does not exist: " + path.toAbsolutePath();
        }
        return Files.readString(path);
    }

    public void addEnterpriseRelease(String filename, String date, String release) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Pattern pattern = Pattern.compile("(## Recent Enterprise Releases:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        String newRelease = String.format("- %s\n  - %s\n", date, release);

        if (matcher.find()) {
            // Section exists, update it
            String existingReleases = matcher.group(2);
            String updatedReleases = newRelease + existingReleases;

            String updatedContent = content.substring(0, matcher.start(2)) +
                    updatedReleases +
                    content.substring(matcher.end(2));
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            // Section doesn't exist, create it after the News section
            Pattern newsPattern = Pattern.compile("(## News:\\s*\\n.*?)(\\n\\n|\\n(?=##)|$)", Pattern.DOTALL);
            Matcher newsMatcher = newsPattern.matcher(content);
            
            if (newsMatcher.find()) {
                String updatedContent = content.substring(0, newsMatcher.end()) +
                        "\n## Recent Enterprise Releases:\n\n" + newRelease +
                        content.substring(newsMatcher.end());
                Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    public void addMultipleEnterpriseReleases(String filename, List<CalendarService.ReleaseEvent> releases) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);

        if (!Files.exists(path)) {
            createNewDocument(filename);
        }

        String content = Files.readString(path);
        Pattern pattern = Pattern.compile("(## Recent Enterprise Releases:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        StringBuilder newReleases = new StringBuilder();
        // Group releases by date
        releases.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getReleaseDate().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d"))))
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
            Pattern newsPattern = Pattern.compile("(## News:\\s*\\n.*?)(\\n\\n|\\n(?=##)|$)", Pattern.DOTALL);
            Matcher newsMatcher = newsPattern.matcher(content);
            
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
        Pattern pattern = Pattern.compile("(## Releases coming soon:\\s*\\n)(.*?)(\\n\\n|\\n(?=##)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            StringBuilder upcomingSection = new StringBuilder();

            // Add releases from calendar
            upcomingReleases.forEach(release ->
                    upcomingSection.append("- ")
                            .append(release.getFormattedRelease())
                            .append(" (")
                            .append(release.getReleaseDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
                            .append(")\n"));

            // Add default items if no calendar releases found
            if (upcomingReleases.isEmpty()) {
                upcomingSection.append("- Micrometer\n")
                        .append("- Micrometer Tracing\n")
                        .append("- Reactor\n")
                        .append("- Reactor Core\n")
                        .append("- Reactor Netty\n")
                        .append("- Reactor Pool\n")
                        .append("- Spring Framework\n")
                        .append("- Spring LDAP\n")
                        .append("- Spring Data\n");
            }

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
}