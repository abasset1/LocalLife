package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.domain.CollectedActivity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DeduplicationServiceTest {

    private final DeduplicationService deduplicationService = new DeduplicationService();

    private CollectedActivity withExternalId(String externalId) {
        return new CollectedActivity(
                "Marché de Noël", "description", LocalDateTime.of(2026, 12, 1, 10, 0), null,
                "marché", 43.2965, 5.3698, "https://example.com", externalId, "OpenAgenda Marseille");
    }

    private CollectedActivity withoutExternalId(String title, LocalDateTime startDate, double lat, double lon) {
        return new CollectedActivity(
                title, "description", startDate, null,
                "marché", lat, lon, "https://example.com", null, "OpenAgenda Marseille");
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
