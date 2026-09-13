package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeduplicationServiceTest {

    private final DeduplicationService deduplicationService = new DeduplicationService();

    private CollectedActivity withExternalId(String externalId) {
        return withoutExternalId(externalId, "Marché de Noël", LocalDateTime.of(2026, 12, 1, 10, 0), 43.2965, 5.3698);
    }

    private CollectedActivity withoutExternalId(String title, LocalDateTime startDate, double lat, double lon) {
        return withoutExternalId(null, title, startDate, lat, lon);
    }

    private CollectedActivity withoutExternalId(
            String externalId, String title, LocalDateTime startDate, double lat, double lon) {
        return new CollectedActivity(
                title, "description", "marché", "https://example.com", externalId, "OpenAgenda Marseille",
                null, null, null, null,
                new CollectedLocation(null, null, null, null, null, null, lat, lon, null),
                List.of(new CollectedTiming(startDate, null)),
                null, List.of(), List.of(), null, List.of(), null);
    }

    @Test
    void computeDeduplicationKey_ShouldUseExternalId_WhenPresent() {
        String key = deduplicationService.computeDeduplicationKey(withExternalId("ext-123"));

        assertTrue(key.startsWith("external:"));
        assertTrue(key.contains("ext-123"));
        assertTrue(key.contains("OpenAgenda Marseille"));
    }

    @Test
    void computeDeduplicationKey_ShouldReturnSameKey_ForSameSourceAndExternalId() {
        String key1 = deduplicationService.computeDeduplicationKey(withExternalId("ext-123"));
        String key2 = deduplicationService.computeDeduplicationKey(withExternalId("ext-123"));

        assertEquals(key1, key2);
    }

    @Test
    void computeDeduplicationKey_ShouldReturnDifferentKeys_ForDifferentExternalIds() {
        String key1 = deduplicationService.computeDeduplicationKey(withExternalId("ext-123"));
        String key2 = deduplicationService.computeDeduplicationKey(withExternalId("ext-456"));

        assertNotEquals(key1, key2);
    }

    @Test
    void computeDeduplicationKey_ShouldFallBackToComposite_WhenExternalIdBlank() {
        String key = deduplicationService.computeDeduplicationKey(withExternalId("   "));

        assertTrue(key.startsWith("composite:"));
    }

    @Test
    void computeDeduplicationKey_ShouldFallBackToComposite_WhenExternalIdNull() {
        String key = deduplicationService.computeDeduplicationKey(withExternalId(null));

        assertTrue(key.startsWith("composite:"));
    }

    @Test
    void computeDeduplicationKey_ShouldReturnSameCompositeKey_ForIdenticalFields() {
        LocalDateTime date = LocalDateTime.of(2026, 12, 1, 10, 0);
        String key1 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", date, 43.2965, 5.3698));
        String key2 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", date, 43.2965, 5.3698));

        assertEquals(key1, key2);
    }

    @Test
    void computeDeduplicationKey_ShouldReturnDifferentCompositeKey_WhenTitleDiffers() {
        LocalDateTime date = LocalDateTime.of(2026, 12, 1, 10, 0);
        String key1 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", date, 43.2965, 5.3698));
        String key2 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Concert", date, 43.2965, 5.3698));

        assertNotEquals(key1, key2);
    }

    @Test
    void computeDeduplicationKey_ShouldReturnDifferentCompositeKey_WhenDateDiffers() {
        String key1 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", LocalDateTime.of(2026, 12, 1, 10, 0), 43.2965, 5.3698));
        String key2 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", LocalDateTime.of(2026, 12, 2, 10, 0), 43.2965, 5.3698));

        assertNotEquals(key1, key2);
    }

    @Test
    void computeDeduplicationKey_ShouldReturnDifferentCompositeKey_WhenLocationDiffers() {
        LocalDateTime date = LocalDateTime.of(2026, 12, 1, 10, 0);
        String key1 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", date, 43.2965, 5.3698));
        String key2 = deduplicationService.computeDeduplicationKey(
                withoutExternalId("Marché de Noël", date, 43.3000, 5.3698));

        assertNotEquals(key1, key2);
    }

}
