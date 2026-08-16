package com.locallife.backend.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la validation des paramètres de recherche géographique
 * (LL-4003/LL-4004/LL-4005), conformément au contrat LL-4001, de la
 * recherche par zone rectangulaire (LL-4006/LL-4007), et de la
 * transmission correcte de plusieurs filtres combinés au repository
 * (LL-4014) : rayon en km (converti en mètres pour le repository),
 * plafonné à 50 km, filtres optionnels par catégorie et par date.
 * Depuis LL-6004, ces deux méthodes ne demandent plus au repository que
 * les activités {@code PUBLISHED} — {@code status} n'est plus un
 * paramètre exposé par ces endpoints publics, voir la javadoc de
 * {@link ActivityService#findNearby}.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private SourceService sourceService;

    private ActivityService activityService() {
        return new ActivityService(activityRepository, geocodingService, sourceService);
    }

    @Test
    void findNearby_ShouldConvertRadiusFromKilometersToMeters_AndDelegateToRepository() {
        // Given
        Activity expected = new Activity(
                1L, "Concert", "desc", "concert", 43.2951, 5.3739, LocalDateTime.now(), null, "PUBLISHED", 1L, null, null);
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, null))
                .thenReturn(List.of(expected));

        // When : radius exprimé en km ("5") doit être converti en mètres (5000) pour le repository.
        List<Activity> result = activityService().findNearby("43.2951", "5.3739", "5", null, null);

        // Then
        verify(activityRepository)
                .findWithinRadius(eq(43.2951), eq(5.3739), eq(5_000.0), eq("PUBLISHED"), isNull(), isNull());
        assertThat(result).containsExactly(expected);
    }

    @Test
    void findNearby_ShouldOnlyEverRequestPublishedStatus_SinceLL6004() {
        // Given : endpoint public (LL-6004) — quels que soient les autres filtres, le statut demandé
        // au repository est toujours PUBLISHED, jamais laissé au choix de l'appelant (contrairement au
        // comportement pré-LL-6003/LL-6004, où un paramètre status existait sur cette méthode).
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert", null))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", "concert", null);

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude", "radius"})
    void findNearby_ShouldThrow_WhenRequiredParamMissing(String missingParam) {
        String latitude = missingParam.equals("latitude") ? null : "43.2951";
        String longitude = missingParam.equals("longitude") ? null : "5.3739";
        String radius = missingParam.equals("radius") ? null : "5";

        assertThatThrownBy(() -> activityService().findNearby(latitude, longitude, radius, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingParam);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findNearby_ShouldThrow_WhenParamIsNotNumeric() {
        assertThatThrownBy(() -> activityService().findNearby("abc", "5.3739", "5", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findNearby_ShouldThrow_WhenLatitudeOutOfRange() {
        assertThatThrownBy(() -> activityService().findNearby("120", "5.3739", "5", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenLongitudeOutOfRange() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "220", "5", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenRadiusIsZeroOrNegative() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "0", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "-1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenRadiusExceedsFiftyKilometers() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "50.01", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    void findNearby_ShouldAccept_WhenRadiusIsExactlyFiftyKilometers() {
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 50_000, "PUBLISHED", null, null))
                .thenReturn(List.of());

        activityService().findNearby("43.2951", "5.3739", "50", null, null);

        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 50_000, "PUBLISHED", null, null);
    }

    @Test
    void findNearby_ShouldPassCategoryThrough_Unchanged_WhenSingleValue() {
        // Given
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert", null))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", "concert", null);

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert", null);
    }

    @Test
    void findNearby_ShouldTrimAndDropEmptyValues_WhenMultipleCategoriesWithSpaces() {
        // Given : la normalisation doit retirer les espaces et les segments vides.
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert,marché", null))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", " concert , marché ,, ", null);

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert,marché", null);
    }

    @Test
    void findNearby_ShouldPassNullCategory_WhenOnlyBlankValuesProvided() {
        // Given : "  , , " ne contient que des segments vides après nettoyage → équivalent à "pas de filtre".
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, null))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", "  , , ", null);

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, null);
    }

    @Test
    void findNearby_ShouldParseAndPassDateThrough_WhenValidIsoDateProvided() {
        // Given
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, LocalDate.of(2026, 9, 5)))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", null, "2026-09-05");

        // Then
        verify(activityRepository)
                .findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, LocalDate.of(2026, 9, 5));
    }

    @Test
    void findNearby_ShouldPassNullDate_WhenDateNotProvided() {
        // Given
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, null))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", null, null);

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", null, null);
    }

    @Test
    void findNearby_ShouldThrow_WhenDateIsNotIsoFormat() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "5", null, "05/09/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findNearby_ShouldThrow_WhenDateDoesNotExist() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "5", null, "2026-02-30"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    // --- findWithinBounds (LL-4006/LL-4007) ---

    @Test
    void findWithinBounds_ShouldDelegateToRepository_WithParsedCoordinates() {
        // Given
        Activity expected = new Activity(
                1L, "Concert", "desc", "concert", 43.30, 5.37, LocalDateTime.now(), null, "PUBLISHED", 1L, null, null);
        when(activityRepository.findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", null, null))
                .thenReturn(List.of(expected));

        // When
        List<Activity> result = activityService()
                .findWithinBounds("43.28", "5.35", "43.31", "5.40", null, null);

        // Then
        verify(activityRepository).findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", null, null);
        assertThat(result).containsExactly(expected);
    }

    @Test
    void findWithinBounds_ShouldOnlyEverRequestPublishedStatus_SinceLL6004() {
        // Given : même règle que findNearby (LL-6004) — voir findNearby_ShouldOnlyEverRequestPublishedStatus_SinceLL6004.
        when(activityRepository.findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", null))
                .thenReturn(List.of());

        // When
        activityService().findWithinBounds("43.28", "5.35", "43.31", "5.40", "concert", null);

        // Then
        verify(activityRepository).findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"swLatitude", "swLongitude", "neLatitude", "neLongitude"})
    void findWithinBounds_ShouldThrow_WhenRequiredParamMissing(String missingParam) {
        String swLatitude = missingParam.equals("swLatitude") ? null : "43.28";
        String swLongitude = missingParam.equals("swLongitude") ? null : "5.35";
        String neLatitude = missingParam.equals("neLatitude") ? null : "43.31";
        String neLongitude = missingParam.equals("neLongitude") ? null : "5.40";

        assertThatThrownBy(() -> activityService()
                .findWithinBounds(swLatitude, swLongitude, neLatitude, neLongitude, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingParam);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findWithinBounds_ShouldThrow_WhenParamIsNotNumeric() {
        assertThatThrownBy(() -> activityService()
                .findWithinBounds("abc", "5.35", "43.31", "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"swLatitude", "neLatitude"})
    void findWithinBounds_ShouldThrow_WhenALatitudeIsOutOfRange(String paramName) {
        String swLatitude = paramName.equals("swLatitude") ? "120" : "43.28";
        String neLatitude = paramName.equals("neLatitude") ? "120" : "43.31";

        assertThatThrownBy(() -> activityService()
                .findWithinBounds(swLatitude, "5.35", neLatitude, "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"swLongitude", "neLongitude"})
    void findWithinBounds_ShouldThrow_WhenALongitudeIsOutOfRange(String paramName) {
        String swLongitude = paramName.equals("swLongitude") ? "220" : "5.35";
        String neLongitude = paramName.equals("neLongitude") ? "220" : "5.40";

        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.28", swLongitude, "43.31", neLongitude, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findWithinBounds_ShouldThrow_WhenSwLatitudeIsNotStrictlyLessThanNeLatitude() {
        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.31", "5.35", "43.31", "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("swLatitude");

        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.35", "5.35", "43.31", "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("swLatitude");

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findWithinBounds_ShouldThrow_WhenSwLongitudeIsNotStrictlyLessThanNeLongitude() {
        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.28", "5.40", "43.31", "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("swLongitude");

        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.28", "5.45", "43.31", "5.40", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("swLongitude");

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findWithinBounds_ShouldPassCategoryThrough_WhenProvided() {
        // Given
        when(activityRepository.findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", null))
                .thenReturn(List.of());

        // When
        activityService().findWithinBounds("43.28", "5.35", "43.31", "5.40", "concert", null);

        // Then
        verify(activityRepository).findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", null);
    }

    @Test
    void findWithinBounds_ShouldParseAndPassDateThrough_WhenValidIsoDateProvided() {
        // Given
        when(activityRepository
                .findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", null, LocalDate.of(2026, 9, 5)))
                .thenReturn(List.of());

        // When
        activityService().findWithinBounds("43.28", "5.35", "43.31", "5.40", null, "2026-09-05");

        // Then
        verify(activityRepository)
                .findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", null, LocalDate.of(2026, 9, 5));
    }

    @Test
    void findWithinBounds_ShouldThrow_WhenDateIsNotIsoFormat() {
        assertThatThrownBy(() -> activityService()
                .findWithinBounds("43.28", "5.35", "43.31", "5.40", null, "05/09/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");

        verifyNoInteractions(activityRepository);
    }

    // --- Combinaison de filtres (LL-4014) ---
    // Les tests ci-dessus vérifient chaque filtre isolément (l'autre à null). Ceux qui
    // suivent vérifient que le service transmet bien les deux filtres au repository quand
    // ils sont fournis en même temps (en plus du statut PUBLISHED toujours fixé, LL-6004),
    // sans en perdre en cours de route.

    @Test
    void findNearby_ShouldPassBothOptionalFiltersThrough_WhenProvidedTogether() {
        // Given
        when(activityRepository.findWithinRadius(
                43.2951, 5.3739, 5_000, "PUBLISHED", "concert", LocalDate.of(2026, 9, 5)))
                .thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", "concert", "2026-09-05");

        // Then
        verify(activityRepository)
                .findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED", "concert", LocalDate.of(2026, 9, 5));
    }

    @Test
    void findWithinBounds_ShouldPassBothOptionalFiltersThrough_WhenProvidedTogether() {
        // Given
        when(activityRepository.findWithinBounds(
                43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", LocalDate.of(2026, 9, 5)))
                .thenReturn(List.of());

        // When
        activityService().findWithinBounds(
                "43.28", "5.35", "43.31", "5.40", "concert", "2026-09-05");

        // Then
        verify(activityRepository)
                .findWithinBounds(43.28, 5.35, 43.31, 5.40, "PUBLISHED", "concert", LocalDate.of(2026, 9, 5));
    }

    // --- findByStatus : consultation administrative (LL-6005) ---

    @Test
    void findByStatus_ShouldDelegateToRepository_WhenStatusIsKnown() {
        // Given
        Activity pending = new Activity(
                1L, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null, "PENDING", 1L, null, null);
        when(activityRepository.findByStatus("PENDING")).thenReturn(List.of(pending));

        // When
        List<Activity> result = activityService().findByStatus("PENDING");

        // Then
        verify(activityRepository).findByStatus("PENDING");
        assertThat(result).containsExactly(pending);
    }

    @Test
    void findByStatus_ShouldAcceptAllThreeKnownValues() {
        for (String status : List.of("PENDING", "PUBLISHED", "REJECTED")) {
            when(activityRepository.findByStatus(status)).thenReturn(List.of());
            activityService().findByStatus(status);
            verify(activityRepository).findByStatus(status);
        }
    }

    @Test
    void findByStatus_ShouldThrow_WhenStatusIsNull() {
        assertThatThrownBy(() -> activityService().findByStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findByStatus_ShouldThrow_WhenStatusIsBlank() {
        assertThatThrownBy(() -> activityService().findByStatus("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findByStatus_ShouldThrow_WhenStatusIsUnknown() {
        assertThatThrownBy(() -> activityService().findByStatus("NOT_A_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");

        verifyNoInteractions(activityRepository);
    }

    // --- publish/reject : transitions de modération (LL-6006) ---

    @Test
    void publish_ShouldChangeStatusToPublished_WhenActivityIsPending() {
        // Given
        Activity pending = new Activity(
                1L, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null, "PENDING", 1L, null, null);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<Activity> result = activityService().publish(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("PUBLISHED");
        // Le reste de l'activité doit rester inchangé, seul le statut change.
        assertThat(result.get().title()).isEqualTo("Concert");
        assertThat(result.get().id()).isEqualTo(1L);
    }

    @Test
    void reject_ShouldChangeStatusToRejected_WhenActivityIsPending() {
        // Given
        Activity pending = new Activity(
                2L, "Marché", "desc", "marché", 43.29, 5.37, LocalDateTime.now(), null, "PENDING", 1L, null, null);
        when(activityRepository.findById(2L)).thenReturn(Optional.of(pending));
        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<Activity> result = activityService().reject(2L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("REJECTED");
    }

    @Test
    void publish_ShouldReturnEmpty_WhenActivityDoesNotExist() {
        // Given
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Activity> result = activityService().publish(99L);

        // Then
        assertThat(result).isEmpty();
        verify(activityRepository, never()).save(any());
    }

    @Test
    void reject_ShouldReturnEmpty_WhenActivityDoesNotExist() {
        // Given
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Activity> result = activityService().reject(99L);

        // Then
        assertThat(result).isEmpty();
        verify(activityRepository, never()).save(any());
    }

    @Test
    void publish_ShouldThrow_WhenActivityIsAlreadyPublished() {
        // Given : transition non prévue par LL-6003 (seul PENDING → PUBLISHED existe).
        Activity published = new Activity(
                3L, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null, "PUBLISHED", 1L, null,
                null);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(published));

        // When / Then
        assertThatThrownBy(() -> activityService().publish(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLISHED");

        verify(activityRepository, never()).save(any());
    }

    @Test
    void reject_ShouldThrow_WhenActivityIsAlreadyRejected() {
        // Given : transition non prévue par LL-6003 (seul PENDING → REJECTED existe).
        Activity rejected = new Activity(
                4L, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null, "REJECTED", 1L, null,
                null);
        when(activityRepository.findById(4L)).thenReturn(Optional.of(rejected));

        // When / Then
        assertThatThrownBy(() -> activityService().reject(4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REJECTED");

        verify(activityRepository, never()).save(any());
    }

    // --- createActivity : validation renforcée (LL-6002, audit LL-6001) ---

    private Source manualSource() {
        return new Source(1L, "Contribution manuelle", "MANUAL", null, "ACTIVE", null);
    }

    @Test
    void createActivity_ShouldThrow_WhenTitleIsNull() {
        assertThatThrownBy(() -> activityService()
                .createActivity(null, "description", "loisir", "1 rue de la Paix, Marseille"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");

        verifyNoInteractions(geocodingService, activityRepository);
    }

    @Test
    void createActivity_ShouldThrow_WhenTitleIsBlank() {
        assertThatThrownBy(() -> activityService()
                .createActivity("   ", "description", "loisir", "1 rue de la Paix, Marseille"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");

        verifyNoInteractions(geocodingService, activityRepository);
    }

    @Test
    void createActivity_ShouldThrow_WhenTitleExceedsMaxLength() {
        String tooLong = "T".repeat(256);

        assertThatThrownBy(() -> activityService()
                .createActivity(tooLong, "description", "loisir", "1 rue de la Paix, Marseille"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");

        verifyNoInteractions(geocodingService, activityRepository);
    }

    @Test
    void createActivity_ShouldThrow_WhenCategoryIsBlankButNotNull() {
        assertThatThrownBy(() -> activityService()
                .createActivity("Pique-nique", "description", "   ", "1 rue de la Paix, Marseille"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");

        verifyNoInteractions(geocodingService, activityRepository);
    }

    @Test
    void createActivity_ShouldSucceed_WhenCategoryIsNull() {
        when(geocodingService.geocode("1 rue de la Paix, Marseille")).thenReturn(new Coordinates(43.29, 5.37));
        when(sourceService.findByType("MANUAL")).thenReturn(Optional.of(manualSource()));
        when(activityRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Activity result = activityService()
                .createActivity("Pique-nique", "description", null, "1 rue de la Paix, Marseille");

        assertThat(result.title()).isEqualTo("Pique-nique");
        assertThat(result.category()).isNull();
        assertThat(result.url()).isNull();
    }

    @Test
    void createActivity_ShouldSaveActivityWithNullUrl_WhenValid() {
        when(geocodingService.geocode("1 rue de la Paix, Marseille")).thenReturn(new Coordinates(43.29, 5.37));
        when(sourceService.findByType("MANUAL")).thenReturn(Optional.of(manualSource()));
        when(activityRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Activity result = activityService()
                .createActivity("Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille");

        assertThat(result.title()).isEqualTo("Pique-nique");
        assertThat(result.category()).isEqualTo("loisir");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.url()).isNull();
        assertThat(result.sourceId()).isEqualTo(1L);
        assertThat(result.importKey()).isNull();
    }

}
