package dev.dashaun.cli.newsletter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.util.List;

@ShellComponent
public class DocumentCommands {

    private final RssService rssService;
    private final DocumentService documentService;

    @Autowired
    public DocumentCommands(RssService rssService, DocumentService documentService) {
        this.rssService = rssService;
        this.documentService = documentService;
    }

    @ShellMethod(value = "Create a new document with template", key = "create")
    public String createDocument(@ShellOption(defaultValue = "spring-update.md") String filename) {
        try {
            documentService.createNewDocument(filename);
            return "Created new document: " + filename;
        } catch (IOException e) {
            return "Error creating document: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Update news section from RSS feed", key = "update-news")
    public String updateNews(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            @ShellOption(defaultValue = "https://spring.io/blog/category/releases.atom") String rssUrl,
            @ShellOption(defaultValue = "10") int limit) {

        try {
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(rssUrl, limit);
            documentService.updateNewsSection(filename, newsItems);
            return String.format("Updated news section with %d items from %s", newsItems.size(), rssUrl);
        } catch (Exception e) {
            return "Error updating news: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Show the current document content", key = "show")
    public String showDocument(@ShellOption(defaultValue = "spring-update.md") String filename) {
        try {
            return documentService.readDocument(filename);
        } catch (IOException e) {
            return "Error reading document: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Add an enterprise release", key = "add-release")
    public String addEnterpriseRelease(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            String date,
            String release) {

        try {
            documentService.addEnterpriseRelease(filename, date, release);
            return "Added enterprise release: " + release + " on " + date;
        } catch (IOException e) {
            return "Error adding release: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Update the demo section", key = "update-demo")
    public String updateDemo(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            String demo) {

        try {
            documentService.updateDemo(filename, demo);
            return "Updated demo section";
        } catch (IOException e) {
            return "Error updating demo: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Fetch latest news from RSS (preview only)", key = "preview-news")
    public String previewNews(
            @ShellOption(defaultValue = "https://spring.io/blog/category/releases.atom") String rssUrl,
            @ShellOption(defaultValue = "5") int limit) {

        try {
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(rssUrl, limit);
            StringBuilder preview = new StringBuilder("Latest news from RSS:\n\n");
            for (RssService.NewsItem item : newsItems) {
                preview.append(item.toString()).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Error fetching news: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Show help for document management", key = "help-doc")
    public String showHelp() {
        return """
                Document Updater Commands:
                
                create [filename]                    - Create a new document with template
                update-news [filename] [rssUrl] [limit] - Update news section from RSS feed
                show [filename]                      - Show current document content
                add-release [filename] date release  - Add an enterprise release
                update-demo [filename] demo          - Update the demo section
                preview-news [rssUrl] [limit]        - Preview latest news from RSS
                
                Examples:
                  create my-doc.md
                  update-news
                  update-news my-doc.md https://spring.io/blog/category/releases.atom 15
                  add-release my-doc.md "July 25" "Spring Boot 3.3.2"
                  update-demo my-doc.md "[Spring Security Demo](https://github.com/example/demo)"
                  preview-news https://spring.io/blog/category/releases.atom 3
                """;
    }
}
