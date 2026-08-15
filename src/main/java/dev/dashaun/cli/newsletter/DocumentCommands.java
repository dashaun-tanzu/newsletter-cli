package dev.dashaun.cli.newsletter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class DocumentCommands {

    static final String DEFAULT_NEWS_FEEDS =
            "https://spring.io/blog.atom,"
            + "https://spring.io/blog/category/releases.atom,"
            + "https://spring.io/blog/category/engineering.atom,"
            + "https://spring.io/blog/category/news.atom";

    private static List<String> splitFeeds(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private final RssService rssService;
    private final DocumentService documentService;
    private final CalendarService calendarService;
    private final YouTubeService youTubeService;
    private final GitHubService gitHubService;
    private final ExitCodeTracker exitCodeTracker;

    @Autowired
    public DocumentCommands(RssService rssService, DocumentService documentService, CalendarService calendarService, YouTubeService youTubeService, GitHubService gitHubService, ExitCodeTracker exitCodeTracker) {
        this.rssService = rssService;
        this.documentService = documentService;
        this.calendarService = calendarService;
        this.youTubeService = youTubeService;
        this.gitHubService = gitHubService;
        this.exitCodeTracker = exitCodeTracker;
    }

    @Command(name = "create", description = "Create a new document with template")
    public String createDocument(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename) {
        try {
            documentService.createNewDocument(filename);
            return "Created new document: " + filename;
        } catch (IOException e) {
            return "Error creating document: " + e.getMessage();
        }
    }

    @Command(name = "update-news", description = "Update news section from RSS feed")
    public String updateNews(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "rssUrl", defaultValue = DEFAULT_NEWS_FEEDS) String rssUrl,
            @Option(longName = "limit", defaultValue = "8") int limit) {

        try {
            List<String> feeds = splitFeeds(rssUrl);
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(feeds, limit);
            documentService.updateNewsSection(filename, newsItems);
            return String.format("Updated news section with %d items from %d feed(s)", newsItems.size(), feeds.size());
        } catch (Exception e) {
            return "Error updating news: " + e.getMessage();
        }
    }

    @Command(name = "show", description = "Show the current document content")
    public String showDocument(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename) {
        try {
            return documentService.readDocument(filename);
        } catch (IOException e) {
            return "Error reading document: " + e.getMessage();
        }
    }

    @Command(name = "update-demo", description = "Update the demo section")
    public String updateDemo(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "demo") String demo) {

        try {
            documentService.updateDemo(filename, demo);
            return "Updated demo section";
        } catch (IOException e) {
            return "Error updating demo: " + e.getMessage();
        }
    }

    @Command(name = "update-github-demos", description = "Update demos section with GitHub repositories ending in '-demo'")
    public String updateGitHubDemos(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename) {
        try {
            List<GitHubService.DemoRepository> demoRepos = gitHubService.fetchDemoRepositories();
            documentService.updateGitHubDemos(filename, demoRepos);
            return String.format("Updated demos section with %d GitHub repositories", demoRepos.size());
        } catch (Exception e) {
            return "Error updating GitHub demos: " + e.getMessage();
        }
    }

    @Command(name = "preview-github-demos", description = "Preview GitHub demo repositories")
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

    @Command(name = "update-youtube", description = "Update YouTube section with latest videos")
    public String updateYouTube(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "limit", defaultValue = "10") int limit) {

        try {
            YouTubeService.FetchResult result = youTubeService.fetchLatest(limit);
            documentService.updateYouTubeSection(filename, result.videos());
            String message = String.format("Updated YouTube section with %d videos", result.videos().size());
            if (!result.isComplete()) {
                // Some content beats none, but a channel missing from the newsletter is a
                // failed run — the caller has to be able to see that.
                exitCodeTracker.markFailure();
                message += ", but no video from: " + String.join(", ", result.missingChannels());
            }
            return message;
        } catch (YouTubeService.YouTubeUnavailableException e) {
            // Leave the existing section alone rather than replacing it with nothing.
            exitCodeTracker.markFailure();
            return "YouTube section left unchanged: " + e.getMessage();
        } catch (Exception e) {
            exitCodeTracker.markFailure();
            return "Error updating YouTube section: " + e.getMessage();
        }
    }

    @Command(name = "preview-youtube", description = "Preview latest YouTube videos (without updating document)")
    public String previewYouTube(
            @Option(longName = "limit", defaultValue = "10") int limit) {
        try {
            YouTubeService.FetchResult result = youTubeService.fetchLatest(limit);
            StringBuilder preview = new StringBuilder("Latest YouTube videos:\n\n");
            for (YouTubeService.YouTubeVideo video : result.videos()) {
                preview.append(video.toString()).append("\n");
            }
            if (!result.isComplete()) {
                preview.append("\nNo video from: ")
                        .append(String.join(", ", result.missingChannels())).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Error fetching YouTube videos: " + e.getMessage();
        }
    }

    @Command(name = "preview-news", description = "Fetch latest news from RSS (preview only)")
    public String previewNews(
            @Option(longName = "rssUrl", defaultValue = DEFAULT_NEWS_FEEDS) String rssUrl,
            @Option(longName = "limit", defaultValue = "5") int limit) {

        try {
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(splitFeeds(rssUrl), limit);
            StringBuilder preview = new StringBuilder("Latest news from RSS:\n\n");
            for (RssService.NewsItem item : newsItems) {
                preview.append(item.toString()).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Error fetching news: " + e.getMessage();
        }
    }

    @Command(name = "update-releases", description = "Update releases from Spring calendar")
    public String updateReleasesFromCalendar(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "calendarUrl", defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @Option(longName = "daysPast", defaultValue = "7") int daysPast) {

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

    @Command(name = "update-upcoming", description = "Update upcoming releases section")
    public String updateUpcomingReleases(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "calendarUrl", defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @Option(longName = "daysAhead", defaultValue = "30") int daysAhead) {

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

    @Command(name = "preview-calendar", description = "Preview calendar releases without updating")
    public String previewCalendarReleases(
            @Option(longName = "calendarUrl", defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @Option(longName = "daysPast", defaultValue = "7") int daysPast,
            @Option(longName = "daysAhead", defaultValue = "30") int daysAhead) {

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

    @Command(name = "full-update", description = "Full document update (news + releases + upcoming + youtube)")
    public String fullUpdate(
            @Option(longName = "filename", defaultValue = "spring-update.md") String filename,
            @Option(longName = "rssUrl", defaultValue = DEFAULT_NEWS_FEEDS) String rssUrl,
            @Option(longName = "calendarUrl", defaultValue = "https://calendar.spring.io/ical") String calendarUrl,
            @Option(longName = "newsLimit", defaultValue = "8") int newsLimit,
            @Option(longName = "daysPast", defaultValue = "7") int daysPast,
            @Option(longName = "daysAhead", defaultValue = "10") int daysAhead,
            @Option(longName = "youtubeLimit", defaultValue = "10") int youtubeLimit) {

        StringBuilder result = new StringBuilder();

        try {
            // Update news
            List<RssService.NewsItem> newsItems = rssService.fetchLatestNews(splitFeeds(rssUrl), newsLimit);
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

            // Update YouTube section. Handled inline so an outage still leaves the remaining
            // sections (demos) to be written, and leaves any existing YouTube content in place
            // rather than replacing it with an empty list.
            try {
                YouTubeService.FetchResult youtube = youTubeService.fetchLatest(youtubeLimit);
                documentService.updateYouTubeSection(filename, youtube.videos());
                if (youtube.isComplete()) {
                    result.append("✓ Updated YouTube section with ").append(youtube.videos().size())
                            .append(" videos\n");
                } else {
                    exitCodeTracker.markFailure();
                    result.append("✗ Updated YouTube section with ").append(youtube.videos().size())
                            .append(" videos, but no video from: ")
                            .append(String.join(", ", youtube.missingChannels())).append('\n');
                }
            } catch (YouTubeService.YouTubeUnavailableException e) {
                exitCodeTracker.markFailure();
                result.append("✗ YouTube section left unchanged: ").append(e.getMessage()).append('\n');
            }

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

    @Command(name = "help-doc", description = "Show help for document management")
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
