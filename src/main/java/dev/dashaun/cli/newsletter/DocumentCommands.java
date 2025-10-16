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
    private final CalendarService calendarService;
    private final YouTubeService youTubeService;
    private final GitHubService gitHubService;

    @Autowired
    public DocumentCommands(RssService rssService, DocumentService documentService, CalendarService calendarService, YouTubeService youTubeService, GitHubService gitHubService) {
        this.rssService = rssService;
        this.documentService = documentService;
        this.calendarService = calendarService;
        this.youTubeService = youTubeService;
        this.gitHubService = gitHubService;
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

    @ShellMethod(value = "Update demos section with GitHub repositories ending in '-demo'", key = "update-github-demos")
    public String updateGitHubDemos(@ShellOption(defaultValue = "spring-update.md") String filename) {
        try {
            List<GitHubService.DemoRepository> demoRepos = gitHubService.fetchDemoRepositories();
            documentService.updateGitHubDemos(filename, demoRepos);
            return String.format("Updated demos section with %d GitHub repositories", demoRepos.size());
        } catch (Exception e) {
            return "Error updating GitHub demos: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Preview GitHub demo repositories", key = "preview-github-demos")
    public String previewGitHubDemos() {
        try {
            List<GitHubService.DemoRepository> demoRepos = gitHubService.fetchDemoRepositories();
            StringBuilder preview = new StringBuilder("GitHub demo repositories:\n\n");
            for (GitHubService.DemoRepository repo : demoRepos) {
                preview.append(repo.toString()).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Error fetching GitHub demos: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Update YouTube section with latest videos", key = "update-youtube")
    public String updateYouTube(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            @ShellOption(defaultValue = "10") int limit) {

        try {
            List<YouTubeService.YouTubeVideo> videos = youTubeService.fetchLatestVideos(limit);
            documentService.updateYouTubeSection(filename, videos);
            return String.format("Updated YouTube section with %d videos", videos.size());
        } catch (Exception e) {
            return "Error updating YouTube section: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Preview latest YouTube videos (without updating document)", key = "preview-youtube")
    public String previewYouTube(@ShellOption(defaultValue = "10") int limit) {
        try {
            List<YouTubeService.YouTubeVideo> videos = youTubeService.fetchLatestVideos(limit);
            StringBuilder preview = new StringBuilder("Latest YouTube videos:\n\n");
            for (YouTubeService.YouTubeVideo video : videos) {
                preview.append(video.toString()).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Error fetching YouTube videos: " + e.getMessage();
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

    @ShellMethod(value = "Update releases from Spring calendar", key = "update-releases")
    public String updateReleasesFromCalendar(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            @ShellOption(defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @ShellOption(defaultValue = "7") int daysPast) {

        try {
            List<CalendarService.ReleaseEvent> recentReleases = calendarService.fetchRecentReleases(calendarUrl, daysPast);

            if (recentReleases.isEmpty()) {
                return "No recent releases found in calendar for the past " + daysPast + " days";
            }

            documentService.addMultipleEnterpriseReleases(filename, recentReleases);
            return String.format("Added %d releases from calendar (past %d days)", recentReleases.size(), daysPast);
        } catch (Exception e) {
            return "Error updating releases from calendar: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Update upcoming releases section", key = "update-upcoming")
    public String updateUpcomingReleases(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            @ShellOption(defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @ShellOption(defaultValue = "30") int daysAhead) {

        try {
            List<CalendarService.ReleaseEvent> upcomingReleases = calendarService.fetchUpcomingReleases(calendarUrl, daysAhead);
            documentService.updateReleasesComingSoon(filename, upcomingReleases);

            if (upcomingReleases.isEmpty()) {
                return "Updated 'Releases coming soon' section with default projects (no calendar events found)";
            } else {
                return String.format("Updated 'Releases coming soon' section with %d upcoming releases", upcomingReleases.size());
            }
        } catch (Exception e) {
            return "Error updating upcoming releases: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Preview calendar releases without updating", key = "preview-calendar")
    public String previewCalendarReleases(
            @ShellOption(defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @ShellOption(defaultValue = "7") int daysPast,
            @ShellOption(defaultValue = "30") int daysAhead) {

        try {
            List<CalendarService.ReleaseEvent> recentReleases = calendarService.fetchRecentReleases(calendarUrl, daysPast);
            List<CalendarService.ReleaseEvent> upcomingReleases = calendarService.fetchUpcomingReleases(calendarUrl, daysAhead);

            StringBuilder preview = new StringBuilder();

            preview.append("Recent Releases (past ").append(daysPast).append(" days):\n");
            if (recentReleases.isEmpty()) {
                preview.append("  No recent releases found\n");
            } else {
                recentReleases.forEach(release ->
                        preview.append("  ").append(release.toString()).append("\n"));
            }

            preview.append("\nUpcoming Releases (next ").append(daysAhead).append(" days):\n");
            if (upcomingReleases.isEmpty()) {
                preview.append("  No upcoming releases found\n");
            } else {
                upcomingReleases.forEach(release ->
                        preview.append("  ").append(release.toString()).append("\n"));
            }

            return preview.toString();
        } catch (Exception e) {
            return "Error fetching calendar releases: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Full document update (news + releases + upcoming + youtube)", key = "full-update")
    public String fullUpdate(
            @ShellOption(defaultValue = "spring-update.md") String filename,
            @ShellOption(defaultValue = "https://spring.io/blog/category/releases.atom") String rssUrl,
            @ShellOption(defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @ShellOption(defaultValue = "10") int newsLimit,
            @ShellOption(defaultValue = "7") int daysPast,
            @ShellOption(defaultValue = "10") int daysAhead,
            @ShellOption(defaultValue = "10") int youtubeLimit) {

        StringBuilder result = new StringBuilder();

        try {
            // Update news
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(rssUrl, newsLimit);
            documentService.updateNewsSection(filename, newsItems);
            result.append("✓ Updated news section with ").append(newsItems.size()).append(" items\n");

            // Update recent releases
            List<CalendarService.ReleaseEvent> recentReleases = calendarService.fetchRecentReleases(calendarUrl, daysPast);
            if (!recentReleases.isEmpty()) {
                documentService.addMultipleEnterpriseReleases(filename, recentReleases);
                result.append("✓ Added ").append(recentReleases.size()).append(" recent releases\n");
            } else {
                result.append("- No recent releases found\n");
            }

            // Update upcoming releases
            List<CalendarService.ReleaseEvent> upcomingReleases = calendarService.fetchUpcomingReleases(calendarUrl, daysAhead);
            documentService.updateReleasesComingSoon(filename, upcomingReleases);
            if (!upcomingReleases.isEmpty()) {
                result.append("✓ Updated upcoming releases with ").append(upcomingReleases.size()).append(" items\n");
            } else {
                result.append("✓ Updated upcoming releases with default projects\n");
            }

            // Update YouTube section
            List<YouTubeService.YouTubeVideo> videos = youTubeService.fetchLatestVideos(youtubeLimit);
            documentService.updateYouTubeSection(filename, videos);
            result.append("✓ Updated YouTube section with ").append(videos.size()).append(" videos\n");

            // Update GitHub demos
            List<GitHubService.DemoRepository> demoRepos = gitHubService.fetchDemoRepositories();
            documentService.updateGitHubDemos(filename, demoRepos);
            result.append("✓ Updated demos section with ").append(demoRepos.size()).append(" GitHub repositories\n");

            result.append("\nDocument fully updated: ").append(filename);
            return result.toString();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "Error during full update: " + e.getMessage();
        }
    }
    @ShellMethod(value = "Show help for document management", key = "help-doc")
    public String showHelp() {
        return """
                Document Updater Commands:
                
                Document Management:
                  create [filename]                         - Create a new document with template
                  show [filename]                           - Show current document content
                
                News Management:
                  update-news [filename] [rssUrl] [limit]   - Update news section from RSS feed
                  preview-news [rssUrl] [limit]             - Preview latest news from RSS
                
                Release Management:
                  update-releases [filename] [calendarUrl] [daysPast] - Update releases from Spring calendar
                  update-upcoming [filename] [calendarUrl] [daysAhead] - Update upcoming releases section
                  preview-calendar [calendarUrl] [daysPast] [daysAhead] - Preview calendar releases
                  add-release [filename] date release       - Manually add an enterprise release
                
                YouTube Management:
                  update-youtube [filename] [limit]         - Update YouTube section with latest videos
                  preview-youtube [limit]                   - Preview latest YouTube videos
                
                Demo Management:
                  update-demo [filename] demo               - Update the demo section manually
                  update-github-demos [filename]           - Update demos with GitHub repositories ending in '-demo'
                  preview-github-demos                      - Preview GitHub demo repositories
                
                Full Update:
                  full-update [filename] [rssUrl] [calendarUrl] [newsLimit] [daysPast] [daysAhead] [youtubeLimit]
                                                            - Update everything at once (includes GitHub demos)
                
                Examples:
                  create my-doc.md
                  update-news
                  update-releases my-doc.md https://calendar.spring.io/ical 14
                  update-upcoming my-doc.md https://calendar.spring.io/ical 60
                  update-youtube my-doc.md 15
                  preview-calendar https://calendar.spring.io/ical 7 30
                  preview-youtube 5
                  full-update my-doc.md
                  add-release my-doc.md "August 12" "Spring Boot 3.3.3"
                  update-demo my-doc.md "[Spring Security Demo](https://github.com/example/demo)"
                """;
    }
}