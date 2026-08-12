package com.locallife.backend.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private GeocodingService geocodingService;

    @Test
    void findNearby_ShouldConvertRadiusFromKilometersToMeters_BeforeCallingRepository() {
        // Given
        ActivityService activityService = new ActivityService(activityRepository, geocodingService);
        Activity expected = new Activity(
                1L, "Concert", "desc", "concert", 43.2951, 5.3739, LocalDateTime.now(), null, "PUBLISHED");
        when(activityRepository.findWithinRadius(43.2951, 5.3739, 5_000)).thenReturn(List.of(expected));

        // When : radius exprimé en km (5) doit être converti en mètres (5000) pour le repository.
        List<Activity> result = activityService.findNearby(43.2951, 5.3739, 5);

        // Then
        verify(activityRepository).findWithinRadius(eq(43.2951), eq(5.3739), eq(5_000.0));
        assertThat(result).containsExactly(expected);
    }

}
