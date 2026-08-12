package com.locallife.backend.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la validation des paramètres de recherche géographique (LL-4003),
 * conformément au contrat LL-4001 : rayon en km (converti en mètres pour le
 * repository), plafonné à 50 km, filtre optionnel par statut.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private GeocodingService geocodingService;

    private ActivityService activityService() {
        return new ActivityService(activityRepository, geocodingService);
    }

    @Test
    void findNearby_ShouldConvertRadiusFromKilometersToMeters_AndDelegateToRepository() {
        // Given
        Activity expected = new Activity(
                1L, "Concert", "desc", "concert", 43.2951, 5.3739, LocalDateTime.now(), null, "PUBLISHED");
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, null)).thenReturn(List.of(expected));

        // When : radius exprimé en km ("5") doit être converti en mètres (5000) pour le repository.
        List<Activity> result = activityService().findNearby("43.2951", "5.3739", "5", null);

        // Then
        verify(activityRepository).findWithinRadius(eq(43.2951), eq(5.3739), eq(5_000.0), isNull());
        assertThat(result).containsExactly(expected);
    }

    @Test
    void findNearby_ShouldPassStatusThrough_WhenProvidedAndKnown() {
        // Given
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED")).thenReturn(List.of());

        // When
        activityService().findNearby("43.2951", "5.3739", "5", "PUBLISHED");

        // Then
        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 5_000, "PUBLISHED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude", "radius"})
    void findNearby_ShouldThrow_WhenRequiredParamMissing(String missingParam) {
        String latitude = missingParam.equals("latitude") ? null : "43.2951";
        String longitude = missingParam.equals("longitude") ? null : "5.3739";
        String radius = missingParam.equals("radius") ? null : "5";

        assertThatThrownBy(() -> activityService().findNearby(latitude, longitude, radius, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingParam);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findNearby_ShouldThrow_WhenParamIsNotNumeric() {
        assertThatThrownBy(() -> activityService().findNearby("abc", "5.3739", "5", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void findNearby_ShouldThrow_WhenLatitudeOutOfRange() {
        assertThatThrownBy(() -> activityService().findNearby("120", "5.3739", "5", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenLongitudeOutOfRange() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "220", "5", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenRadiusIsZeroOrNegative() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "0", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_ShouldThrow_WhenRadiusExceedsFiftyKilometers() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "50.01", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    void findNearby_ShouldAccept_WhenRadiusIsExactlyFiftyKilometers() {
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 50_000, null)).thenReturn(List.of());

        activityService().findNearby("43.2951", "5.3739", "50", null);

        verify(activityRepository).findWithinRadius(43.2951, 5.3739, 50_000, null);
    }

    @Test
    void findNearby_ShouldThrow_WhenStatusIsUnknown() {
        assertThatThrownBy(() -> activityService().findNearby("43.2951", "5.3739", "5", "NOT_A_STATUS"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(activityRepository);
    }

}
