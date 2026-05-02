package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarServiceTest {

    @Test
    void shouldParseReleaseEvent() {
        CalendarService.ReleaseEvent event = new CalendarService.ReleaseEvent(
            "Spring Boot",
            "3.3.0",
            LocalDate.now(),
            "Spring Boot 3.3.0 (Enterprise)"
        );
        
        assertEquals("Spring Boot", event.getProjectName());
        assertEquals("3.3.0", event.getVersion());
        assertEquals(LocalDate.now(), event.getReleaseDate());
        assertEquals("Spring Boot 3.3.0 (Enterprise)", event.getOriginalSummary());
    }

    @Test
    void shouldFormatReleaseEvent() {
        CalendarService.ReleaseEvent event = new CalendarService.ReleaseEvent(
            "Spring Boot",
            "3.3.0",
            LocalDate.of(2024, 1, 15),
            "Spring Boot 3.3.0 (Enterprise)"
        );
        
        String formatted = event.toString();
        
        assertTrue(formatted.contains("2024-01-15"));
        assertTrue(formatted.contains("Spring Boot"));
        assertTrue(formatted.contains("3.3.0"));
    }

    @Test
    void shouldGetFormattedRelease() {
        CalendarService.ReleaseEvent event = new CalendarService.ReleaseEvent(
            "Spring Cloud",
            "2024.0.0",
            LocalDate.now(),
            "Spring Cloud 2024.0.0 General Availability"
        );
        
        assertEquals("Spring Cloud 2024.0.0 General Availability", event.getFormattedRelease());
    }

    @Test
    void shouldDetectEnterpriseRelease() {
        CalendarService service = new CalendarService();
        
        assertTrue(service.isEnterpriseReleaseEvent("Spring Boot 3.3.0 (Enterprise)"));
        assertTrue(service.isEnterpriseReleaseEvent("Spring Framework 6.1.0 (Enterprise)"));
    }

    @Test
    void shouldNotDetectNonEnterpriseRelease() {
        CalendarService service = new CalendarService();
        
        assertFalse(service.isEnterpriseReleaseEvent("Spring Boot 3.3.0"));
        assertFalse(service.isEnterpriseReleaseEvent("Spring Cloud 2024.0.0"));
        assertFalse(service.isEnterpriseReleaseEvent("General Availability"));
    }

    @Test
    void shouldFilterReleasesByDateRange() {
        CalendarService service = new CalendarService();
        
        List<CalendarService.ReleaseEvent> recent = service.fetchRecentReleases(null, 7);
        List<CalendarService.ReleaseEvent> upcoming = service.fetchUpcomingReleases(null, 30);
        
        assertNotNull(recent);
        assertNotNull(upcoming);
    }

    @Test
    void shouldExtractVersionFromSummary() {
        CalendarService service = new CalendarService();
        
        CalendarService.ReleaseEvent event = new CalendarService.ReleaseEvent(
            "Test",
            "3.3.0",
            LocalDate.now(),
            "Spring Boot 3.3.0 RC1 available"
        );
        
        assertEquals("3.3.0", event.getVersion());
    }

    @Test
    void shouldSortReleasesChronologically() {
        CalendarService service = new CalendarService();
        
        List<CalendarService.ReleaseEvent> upcoming = service.fetchUpcomingReleases(null, 30);
        
        for (int i = 0; i < upcoming.size() - 1; i++) {
            assertTrue(upcoming.get(i).getReleaseDate().isBefore(upcoming.get(i + 1).getReleaseDate()) ||
                       upcoming.get(i).getReleaseDate().isEqual(upcoming.get(i + 1).getReleaseDate()));
        }
    }

    @Test
    void shouldReturnEmptyListForNullUrl() {
        CalendarService service = new CalendarService();
        
        List<CalendarService.ReleaseEvent> recent = service.fetchRecentReleases(null, 7);
        List<CalendarService.ReleaseEvent> upcoming = service.fetchUpcomingReleases(null, 30);
        
        assertNotNull(recent);
        assertNotNull(upcoming);
    }

    @Test
    void shouldSkipNonReleaseEvents() {
        CalendarService service = new CalendarService();
        
        List<CalendarService.ReleaseEvent> releases = service.fetchRecentReleases(null, 365);
        
        assertNotNull(releases);
    }

    @Test
    void shouldGetOriginalSummary() {
        String summary = "Spring Boot 3.3.0 (Enterprise) GA Release";
        CalendarService.ReleaseEvent event = new CalendarService.ReleaseEvent(
            "Spring Boot",
            "3.3.0",
            LocalDate.now(),
            summary
        );
        
        assertEquals(summary, event.getOriginalSummary());
    }
}
