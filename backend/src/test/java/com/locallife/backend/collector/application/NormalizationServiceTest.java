package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.collector.domain.CollectedAccessibility;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedMedia;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.metadata.domain.ActivityMetadata;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.application.RecurrenceRuleService;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.tag.domain.ActivityTag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NormalizationServiceTest {

    private final NormalizationService normalizationService = new NormalizationService(new RecurrenceRuleService());

    private static final CollectedLocation LOCATION =
            new CollectedLocation("Vieux-Port", null, null, null, null, null, 43.2965, 5.3698, null);

    private static final List<CollectedTiming> SINGLE_TIMING = List.of(
            new CollectedTiming(LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 24, 20, 0)));

    private CollectedActivity validCollectedActivity() {
        return new CollectedActivity(
                "Marché de Noël",
                "Marché de Noël sur le Vieux-Port",
                "marché",
                "https://example.com/evenement/123",
                "ext-123",
                "OpenAgenda Marseille",
                null, null, null, null,
                LOCATION,
                SINGLE_TIMING,
                null, List.of(), List.of(), null, List.of(), null);
    }

    private CollectedActivity withLocation(CollectedLocation location) {
        return withLocationAndTimings(location, SINGLE_TIMING);
    }

    private CollectedActivity withTimings(List<CollectedTiming> timings) {
        return withLocationAndTimings(LOCATION, timings);
    }

    private CollectedActivity withLocationAndTimings(CollectedLocation location, List<CollectedTiming> timings) {
        return new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, location, timings, null, List.of(), List.of(), null, List.of(), null);
    }

    @Test
    void normalize_ShouldConvertValidData_ToPublishedActivity() {
        CollectedActivity collected = validCollectedActivity();

        Optional<Activity> result = normalizationService.normalize(collected);

        assertTrue(result.isPresent());
        Activity activity = result.get();
        assertEquals("Marché de Noël", activity.title());
        assertEquals("Marché de Noël sur le Vieux-Port", activity.description());
        assertEquals("marché", activity.category());
        assertEquals(43.2965, activity.latitude());
        assertEquals(5.3698, activity.longitude());
        assertEquals(LocalDateTime.of(2026, 12, 1, 10, 0), activity.startDate());
        assertEquals(LocalDateTime.of(2026, 12, 24, 20, 0), activity.endDate());
        assertEquals("PUBLISHED", activity.status());
    }

    @Test
    void normalize_ShouldPropagateAddressCityAndPostalCode_WhenPresent() {
        // LL-10004 : garantit que address/city/postalCode collectés par un
        // collector (ex. OpenAgendaCollector) traversent la normalisation
        // jusqu'à l'Activity finale, comme url (LL-6002).
        CollectedLocation location =
                new CollectedLocation(null, "Quai du Port", "Marseille", "13002", null, null, 43.2965, 5.3698, null);
        Optional<Activity> result = normalizationService.normalize(withLocation(location));

        assertTrue(result.isPresent());
        Activity activity = result.get();
        assertEquals("Quai du Port", activity.address());
        assertEquals("Marseille", activity.city());
        assertEquals("13002", activity.postalCode());
    }

    @Test
    void normalize_ShouldKeepAddressCityPostalCodeNull_WhenAbsentFromCollectedData() {
        // LL-10004 : aucune ville/adresse ne doit être inventée quand la
        // source ne les fournit pas — null propagé tel quel.
        Optional<Activity> result = normalizationService.normalize(validCollectedActivity());

        assertTrue(result.isPresent());
        Activity activity = result.get();
        assertNull(activity.address());
        assertNull(activity.city());
        assertNull(activity.postalCode());
    }

    @Test
    void normalize_ShouldRejectData_WhenTitleIsBlank() {
        CollectedActivity collected = new CollectedActivity(
                "   ", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenTitleIsNull() {
        CollectedActivity collected = new CollectedActivity(
                null, "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenLocationIsNull() {
        assertFalse(normalizationService.normalize(withLocation(null)).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenTimingsIsEmpty() {
        assertFalse(normalizationService.normalize(withTimings(List.of())).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenAllTimingsHaveNoBegin() {
        assertFalse(normalizationService.normalize(withTimings(List.of(new CollectedTiming(null, null))))
                .isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenLatitudeOutOfRange() {
        CollectedLocation location =
                new CollectedLocation(null, null, null, null, null, null, 91.0, 5.3698, null);
        assertFalse(normalizationService.normalize(withLocation(location)).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenLongitudeOutOfRange() {
        CollectedLocation location =
                new CollectedLocation(null, null, null, null, null, null, 43.2965, 181.0, null);
        assertFalse(normalizationService.normalize(withLocation(location)).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenDescriptionAndCategoryAreNull() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", null, null, "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertTrue(normalizationService.normalize(collected).isPresent());
    }

    // --- Renforcement de la validation (LL-6002, audit LL-6001) ---

    @Test
    void normalize_ShouldRejectData_WhenTitleExceedsMaxLength() {
        CollectedActivity collected = new CollectedActivity(
                "T".repeat(256), "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenTitleIsExactlyMaxLength() {
        CollectedActivity collected = new CollectedActivity(
                "T".repeat(255), "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertTrue(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenEndDateIsBeforeStartDate() {
        List<CollectedTiming> timings = List.of(new CollectedTiming(
                LocalDateTime.of(2026, 12, 24, 20, 0), LocalDateTime.of(2026, 12, 1, 10, 0)));

        assertFalse(normalizationService.normalize(withTimings(timings)).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenEndDateEqualsStartDate() {
        LocalDateTime sameInstant = LocalDateTime.of(2026, 12, 1, 10, 0);
        List<CollectedTiming> timings = List.of(new CollectedTiming(sameInstant, sameInstant));

        assertTrue(normalizationService.normalize(withTimings(timings)).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenCategoryIsBlankButNotNull() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "   ", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenUrlIsMalformed() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "pas une url", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldRejectData_WhenUrlSchemeIsNotHttpOrHttps() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "ftp://example.com/fichier", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenUrlIsNull() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", null, "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        assertTrue(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldCarryUrlThrough_ToNormalizedActivity() {
        Optional<Activity> result = normalizationService.normalize(validCollectedActivity());

        assertTrue(result.isPresent());
        assertEquals("https://example.com/evenement/123", result.get().url());
    }

    @Test
    void normalize_ShouldRejectData_WhenRecurrenceRuleIsInvalid() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, "PAS UN RRULE",
                List.of(), List.of(), null, List.of(), null);

        assertFalse(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldAcceptData_WhenRecurrenceRuleIsValid() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, "FREQ=WEEKLY;BYDAY=MO",
                List.of(), List.of(), null, List.of(), null);

        assertTrue(normalizationService.normalize(collected).isPresent());
    }

    @Test
    void normalize_ShouldCarryRichFieldsThrough_ToNormalizedActivity() {
        // LL-11006 : longDescription/conditions/ageMin/ageMax doivent être conservés lorsqu'ils
        // existent dans la donnée collectée, sans transformation.
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                "Description détaillée sur plusieurs lignes.", "Gratuit, réservation conseillée.", 6, 12,
                LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(), null);

        Optional<Activity> result = normalizationService.normalize(collected);

        assertTrue(result.isPresent());
        assertEquals("Description détaillée sur plusieurs lignes.", result.get().longDescription());
        assertEquals("Gratuit, réservation conseillée.", result.get().conditions());
        assertEquals(6, result.get().ageMin());
        assertEquals(12, result.get().ageMax());
    }

    @Test
    void normalize_ShouldAccept_WhenRichFieldsAreAbsent() {
        // « conservées lorsqu'elles existent » : leur absence ne doit pas faire rejeter l'activité.
        Optional<Activity> result = normalizationService.normalize(validCollectedActivity());

        assertTrue(result.isPresent());
        assertNull(result.get().longDescription());
        assertNull(result.get().conditions());
        assertNull(result.get().ageMin());
        assertNull(result.get().ageMax());
    }

    // --- LL-11011 : enrichissement (Location/Schedule/Occurrence/Media/Links/Accessibility/Tags/Metadata) ---

    @Test
    void normalizeLocation_ShouldReturnEmpty_WhenLocationIsNull() {
        assertTrue(normalizationService.normalizeLocation(withLocation(null)).isEmpty());
    }

    @Test
    void normalizeLocation_ShouldReturnEmpty_WhenLocationHasNoNameAddressOrCoordinates() {
        CollectedLocation empty = new CollectedLocation(null, null, null, null, null, null, null, null, null);
        assertTrue(normalizationService.normalizeLocation(withLocation(empty)).isEmpty());
    }

    @Test
    void normalizeLocation_ShouldBuildLocation_WhenCoordinatesPresent() {
        Optional<Location> result = normalizationService.normalizeLocation(validCollectedActivity());

        assertTrue(result.isPresent());
        assertEquals("Vieux-Port", result.get().name());
        assertEquals(43.2965, result.get().latitude());
        assertEquals(5.3698, result.get().longitude());
    }

    @Test
    void normalizeLocation_ShouldMarkGenericPlaceAsApproximate_AndNeverExact() {
        // ADR-0011, phase A : texte générique (nom, adresse vidée par le collecteur DATAtourisme) → APPROXIMATE.
        CollectedLocation generic = new CollectedLocation(
                "Centre ville", null, "Apt", "84400", null, "FR", 43.876, 5.396, null);
        CollectedLocation precise = new CollectedLocation(
                "Théâtre des Halles", "Rue du Roi René", "Avignon", "84000", null, "FR", 43.946, 4.809, null);

        assertEquals(CoordinatePrecision.APPROXIMATE,
                normalizationService.normalizeLocation(withLocation(generic)).orElseThrow().coordinatePrecision());
        assertEquals(CoordinatePrecision.UNKNOWN,
                normalizationService.normalizeLocation(withLocation(precise)).orElseThrow().coordinatePrecision());
        assertEquals(CoordinatePrecision.UNKNOWN,
                normalizationService.normalizeLocation(validCollectedActivity()).orElseThrow().coordinatePrecision());
    }

    @Test
    void normalizeSchedule_ShouldReturnEmpty_WhenActivityIdIsNull() {
        assertTrue(normalizationService.normalizeSchedule(validCollectedActivity(), null, 1L).isEmpty());
    }

    @Test
    void normalizeSchedule_ShouldBuildSchedule_FromFirstTiming() {
        Optional<Schedule> result = normalizationService.normalizeSchedule(validCollectedActivity(), 1L, 2L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().activityId());
        assertEquals(2L, result.get().locationId());
        assertEquals(java.time.LocalTime.of(10, 0), result.get().startTime());
    }

    @Test
    void normalizeOccurrences_ShouldReturnOneOccurrencePerTiming() {
        List<CollectedTiming> timings = List.of(
                new CollectedTiming(LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 1, 12, 0)),
                new CollectedTiming(LocalDateTime.of(2026, 12, 8, 10, 0), LocalDateTime.of(2026, 12, 8, 12, 0)));

        List<Occurrence> result = normalizationService.normalizeOccurrences(withTimings(timings), 5L, 2L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(o -> o.scheduleId().equals(5L)));
    }

    @Test
    void normalizeMedia_ShouldFilterOutBlankUrls() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null,
                List.of(new CollectedMedia("https://img.example.com/a.jpg", "alt", "credit", 800, 600),
                        new CollectedMedia("  ", null, null, null, null)),
                List.of(), null, List.of(), null);

        List<Media> result = normalizationService.normalizeMedia(collected);

        assertEquals(1, result.size());
        assertEquals("https://img.example.com/a.jpg", result.get(0).url());
    }

    @Test
    void normalizeLinks_ShouldBuildActivityLinks() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(),
                List.of(new CollectedLink("WEBSITE", "Site officiel", "https://example.com/site")),
                null, List.of(), null);

        List<ActivityLink> result = normalizationService.normalizeLinks(collected, 1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).activityId());
        assertEquals("https://example.com/site", result.get(0).value());
    }

    @Test
    void normalizeAccessibility_ShouldReturnEmpty_WhenNothingKnown() {
        CollectedAccessibility unknown = new CollectedAccessibility(null, null, null, null, null);
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), unknown, List.of(),
                null);

        assertTrue(normalizationService.normalizeAccessibility(collected, 1L).isEmpty());
    }

    @Test
    void normalizeAccessibility_ShouldBuildAccessibility_WhenAtLeastOneFieldKnown() {
        CollectedAccessibility known = new CollectedAccessibility(true, null, null, null, null);
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), known, List.of(), null);

        Optional<Accessibility> result = normalizationService.normalizeAccessibility(collected, 1L);

        assertTrue(result.isPresent());
        assertEquals(Boolean.TRUE, result.get().motorImpairment());
    }

    @Test
    void normalizeTags_ShouldFilterBlankAndDeduplicate() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null,
                List.of("marché", "noël", "marché", "   "), null);

        List<ActivityTag> result = normalizationService.normalizeTags(collected, 1L);

        assertEquals(2, result.size());
    }

    @Test
    void normalizeTags_ShouldTruncateTagsLongerThanTheColumn() {
        // Un tag trop long faisait échouer l'insertion, donc tout l'enrichissement de l'activité.
        String longTag = "a".repeat(140);
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null,
                List.of(longTag, " marché "), null);

        List<ActivityTag> result = normalizationService.normalizeTags(collected, 1L);

        assertEquals(List.of("a".repeat(100), "marché"), result.stream().map(ActivityTag::tag).toList());
    }

    @Test
    void normalizeMetadata_ShouldReturnEmpty_WhenRegistrationInfoIsBlank() {
        assertTrue(normalizationService.normalizeMetadata(validCollectedActivity(), 1L).isEmpty());
    }

    @Test
    void normalizeMetadata_ShouldBuildJsonMetadata_WhenRegistrationInfoPresent() {
        CollectedActivity collected = new CollectedActivity(
                "Titre", "description", "marché", "https://example.com", "ext-1", "OpenAgenda Marseille",
                null, null, null, null, LOCATION, SINGLE_TIMING, null, List.of(), List.of(), null, List.of(),
                "Inscription obligatoire avant le 1er décembre.");

        Optional<ActivityMetadata> result = normalizationService.normalizeMetadata(collected, 1L);

        assertTrue(result.isPresent());
        assertTrue(result.get().data().value().contains("Inscription obligatoire"));
    }

}
