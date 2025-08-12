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
            "(News:\\s*\\n)(.*?)(\\n\\n|\\n(?=[A-Z][^:\\n]*:)|$)",
            Pattern.DOTALL
    );

    public void createNewDocument(String filename) throws IOException {
        Path path = Path.of(filename != null ? filename : DEFAULT_FILENAME);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d"));

        String template = String.format("""
                # %s
                
                News:
                
                
                Enterprise Releases:
                
                
                Releases coming soon:
                - Micrometer
                - Micrometer Tracing
                - Reactor
                - Reactor Core
                - Reactor Netty
                - Reactor Pool
                - Spring Framework
                - Spring LDAP
                - Spring Data
                
                Demo:
                
                
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
                        "\n\nNews:\n\n" + newsSection +
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
        Pattern pattern = Pattern.compile("(Enterprise Releases:\\s*\\n)(.*?)(\\n\\n|\\n(?=[A-Z][^:\\n]*:)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String existingReleases = matcher.group(2);
            String newRelease = String.format("- %s\n  - %s\n", date, release);
            String updatedReleases = newRelease + existingReleases;

            String updatedContent = content.substring(0, matcher.start(2)) +
                    updatedReleases +
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
        Pattern pattern = Pattern.compile("(Demo:\\s*\\n)(.*?)$", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String updatedContent = content.substring(0, matcher.start(2)) +
                    "\n" + demoDescription + "\n\n";
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}