package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.collector.domain.CollectedActivity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NormalizationServiceTest {

    private final NormalizationService normalizationService = new NormalizationService();

    private CollectedActivity validCollectedActivity() {
        return new CollectedActivity(
                "Marché de Noël",
                "Marché de Noël sur le Vieux-Port",
                LocalDateTime.of(2026, 12, 1, 10, 0),
                LocalDateTime.of(2026, 12, 24, 20, 0),
                "marché",
                43.2965,
                5.3698,
                "https://example.com/evenement/123",
                "ext-123",
                "OpenAgenda Marseille");
    }

    @Test
    void normalize_ShouldConvertValidData_ToPublishedActivity() {
        // Given
        CollectedActivity collected = validCollectedActivity();

        // When
        Optional<Activity> result = normalizationService.normalize(collected);

        // Then
        assertTrue(result.isPresent());
        Activity activity = result.get();
        assertEquals("Marché de Noël", activity.title());
        assertEquals("Marché de Noël sur le Vieux-Port", activity.description());
        assertEquals("marché", activity.category());
        assertEquals(43.2965, activity.latitude());
        assertEquals(5.3698, activity.longitude());
        assertEquals(collected.startDate(), activity.startDate());
        assertEquals(collected.endDate(), activity.endDate());
        assertEquals("PUBLISHED", activity.status());
    }

    @Test
    void normalize_ShouldRejectData_WhenTitleIsBlank() {
        CollectedActivity collected = new CollectedActivity(
                "   ", "description", LocalDateTime.now(), null, "marché",
                43.2965, 5.3698, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenTitleIsNull() {
        CollectedActivity collected = new CollectedActivity(
                null, "description", LocalDateTime.now(), null, "marché",
                43.2965, 5.3698, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenStartDateIsNull() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", null, null, "marché",
                43.2965, 5.3698, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenLatitudeOutOfRange() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", LocalDateTime.now(), null, "marché",
                91, 5.3698, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenLongitudeOutOfRange() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", LocalDateTime.now(), null, "marché",
                43.2965, 181, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenDescriptionAndCategoryAndEndDateAreNull() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", null, LocalDateTime.now(), null, null,
                43.2965, 5.3698, "https://example.com", "ext-1", "OpenAgenda Marseille");

        assertTrue(normalizationService.normalize(collected).isPresent());
    }

}
