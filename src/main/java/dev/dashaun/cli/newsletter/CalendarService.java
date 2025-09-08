package dev.dashaun.cli.newsletter;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Summary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CalendarService {

    private final WebClient webClient;
    private static final String DEFAULT_CALENDAR_URL = "https://calendar.spring.io/ical";

    // Pattern to extract version numbers from event summaries
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?(?:-[A-Z0-9]+)?)", Pattern.CASE_INSENSITIVE);

    public CalendarService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 5))
                .build();
    }

    public List<ReleaseEvent> fetchUpcomingReleases(String calendarUrl, int daysAhead) {
        try {
            String icalContent = webClient.get()
                    .uri(calendarUrl != null ? calendarUrl : DEFAULT_CALENDAR_URL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(new StringReader(icalContent));

            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusDays(daysAhead);

            List<ReleaseEvent> releases = new ArrayList<>();

            for (Object component : calendar.getComponents()) {
                if (component instanceof VEvent) {
                    VEvent event = (VEvent) component;
                    if(!isEnterpriseReleaseEvent(event.getSummary().getValue())) {
                    ReleaseEvent release = parseReleaseEvent(event, today, endDate);
                    if (release != null) {
                        releases.add(release);
                    }
                    }
                }
            }

            return releases.stream()
                    .sorted((a, b) -> a.getReleaseDate().compareTo(b.getReleaseDate()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch calendar: " + e.getMessage(), e);
        }
    }

    public List<ReleaseEvent> fetchRecentReleases(String calendarUrl, int daysPast) {
        try {
            String icalContent = webClient.get()
                    .uri(calendarUrl != null ? calendarUrl : DEFAULT_CALENDAR_URL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(new StringReader(icalContent));

            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(daysPast);

            List<ReleaseEvent> releases = new ArrayList<>();

            for (Object component : calendar.getComponents()) {
                if (component instanceof VEvent) {
                    VEvent event = (VEvent) component;
                    if(isEnterpriseReleaseEvent(event.getSummary().getValue())) {
                        ReleaseEvent release = parseReleaseEvent(event, startDate, today);
                        if (release != null) {
                            releases.add(release);
                        }
                    }
                }
            }

            return releases.stream()
                    .sorted((a, b) -> a.getReleaseDate().compareTo(b.getReleaseDate())) // Oldest first
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch calendar: " + e.getMessage(), e);
        }
    }

    private ReleaseEvent parseReleaseEvent(VEvent event, LocalDate startDate, LocalDate endDate) {
        try {
            Summary summary = event.getSummary();
            var dtStartOpt = event.getStartDate();

            if (summary == null || dtStartOpt.isEmpty()) {
                return null;
            }

            DtStart<?> dtStart = dtStartOpt.get();
            LocalDate eventDate = LocalDate.from(dtStart.getDate());

            // Check if event is within our date range
            if (eventDate.isBefore(startDate) || eventDate.isAfter(endDate)) {
                return null;
            }

            String eventSummary = summary.getValue();

            // Skip events that don't look like releases
            if (!isReleaseEvent(eventSummary)) {
                return null;
            }

            // Extract project name and version
            ProjectInfo projectInfo = extractProjectInfo(eventSummary);
            if (projectInfo == null) {
                return null;
            }

            return new ReleaseEvent(
                    projectInfo.getProjectName(),
                    projectInfo.getVersion(),
                    eventDate,
                    eventSummary
            );

        } catch (Exception e) {
            // Skip events that can't be parsed
            return null;
        }
    }

    private boolean isReleaseEvent(String summary) {
        String lowerSummary = summary.toLowerCase();
        return (lowerSummary.contains("release") ||
                lowerSummary.contains("spring") ||
                VERSION_PATTERN.matcher(summary).find()) &&
                !lowerSummary.contains("meeting") &&
                !lowerSummary.contains("planning");
    }

    private boolean isEnterpriseReleaseEvent(String summary){
        return (summary.contains("(Enterprise)"));
    }

    private ProjectInfo extractProjectInfo(String summary) {
        // Common patterns for Spring project releases
        String[] patterns = {
                "(?i)([A-Za-z\\s]+?)\\s+(\\d+\\.\\d+(?:\\.\\d+)?(?:-[A-Z0-9]+)?)(?:\\s+release)?",
                "(?i)([A-Za-z\\s]+?)\\s+v?(\\d+\\.\\d+(?:\\.\\d+)?(?:-[A-Z0-9]+)?)",
                "(?i)release\\s+([A-Za-z\\s]+?)\\s+(\\d+\\.\\d+(?:\\.\\d+)?(?:-[A-Z0-9]+)?)"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(summary);
            if (matcher.find()) {
                String projectName = matcher.group(1).trim();
                String version = matcher.group(2).trim();

                // Clean up project name
                projectName = cleanProjectName(projectName);

                if (!projectName.isEmpty() && !version.isEmpty()) {
                    return new ProjectInfo(projectName, version);
                }
            }
        }

        return null;
    }

    private String cleanProjectName(String projectName) {
        // Remove common prefixes/suffixes and normalize
        return projectName
                .replaceAll("(?i)^(release|spring)\\s+", "")
                .replaceAll("(?i)\\s+(release|rc|ga|final)$", "")
                .trim();
    }

    public static class ReleaseEvent {
        private final String projectName;
        private final String version;
        private final LocalDate releaseDate;
        private final String originalSummary;

        public ReleaseEvent(String projectName, String version, LocalDate releaseDate, String originalSummary) {
            this.projectName = projectName;
            this.version = version;
            this.releaseDate = releaseDate;
            this.originalSummary = originalSummary;
        }

        public String getProjectName() { return projectName; }
        public String getVersion() { return version; }
        public LocalDate getReleaseDate() { return releaseDate; }
        public String getOriginalSummary() { return originalSummary; }

        public String getFormattedRelease() {
            return String.format("%s", originalSummary);
        }

        @Override
        public String toString() {
            return String.format("%s - %s %s",
                    releaseDate.toString(),
                    projectName,
                    version);
        }
    }

    private static class ProjectInfo {
        private final String projectName;
        private final String version;

        public ProjectInfo(String projectName, String version) {
            this.projectName = projectName;
            this.version = version;
        }

        public String getProjectName() { return projectName; }
        public String getVersion() { return version; }
    }
}
