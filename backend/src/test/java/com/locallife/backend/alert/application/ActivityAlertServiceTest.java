package com.locallife.backend.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAlertServiceTest {

    @Mock
    private ActivityAlertRepository activityAlertRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private ActivityAlertService activityAlertService;

    private void givenCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                new Category(1L, "SPORT", true, LocalDateTime.now(), LocalDateTime.now(), Set.of()),
                new Category(2L, "CULTURE", true, LocalDateTime.now(), LocalDateTime.now(), Set.of())));
    }

    @Test
    void createAlert_ShouldStoreOfficialCity_AndCleanedCategories() {
        givenCategories();
        when(geocodingService.resolveCity("lyon", null, null)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert created = activityAlertService.createAlert(
                1L, Arrays.asList("SPORT", " CULTURE", "SPORT", null), " lyon ", null, null, 10);

        assertEquals("Lyon", created.city());
        assertEquals(45.76, created.latitude());
        assertEquals(List.of("CULTURE", "SPORT"), created.categoryCodes());
        verify(activityAlertRepository).create(any(), argThat(token -> token.length() == 64));
    }

    @Test
    void createAlert_ShouldUsePosition_WhenProvided() {
        givenCategories();
        when(geocodingService.resolveCity(null, 43.21, 5.54)).thenReturn(new Coordinates(43.2, 5.5, "Cassis", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert created = activityAlertService.createAlert(1L, List.of("SPORT"), null, 43.21, 5.54, 20);

        assertEquals("Cassis", created.city());
    }

    @Test
    void createAlert_ShouldRequireAtLeastOneCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of(), "Lyon", null, null, 10));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, null, "Lyon", null, null, 10));
        verify(activityAlertRepository, never()).create(any(), anyString());
    }

    @Test
    void createAlert_ShouldRejectUnknownCategory_InvalidRadius_AndMissingCity() {
        givenCategories();

        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("NOPE"), "Lyon", null, null, 10));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Lyon", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Lyon", null, null, 51));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), " ", null, null, 10));
        verify(geocodingService, never()).resolveCity(any(), any(), any());
    }

    @Test
    void createAlert_ShouldRejectWhenLimitReached() {
        givenCategories();
        when(activityAlertRepository.countByUserId(1L)).thenReturn(ActivityAlertService.MAX_ALERTS_PER_USER);

        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Lyon", null, null, 10));
        verify(geocodingService, never()).resolveCity(any(), any(), any());
    }

    @Test
    void createAlert_ShouldPropagateCityNotFound() {
        givenCategories();
        when(geocodingService.resolveCity("Zzzz", null, null)).thenThrow(new CityNotFoundException("introuvable"));

        assertThrows(CityNotFoundException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Zzzz", null, null, 10));
        verify(activityAlertRepository, never()).create(any(), anyString());
    }

    @Test
    void deleteAlert_ShouldThrow_WhenNotOwned() {
        when(activityAlertRepository.deleteByIdAndUserId(5L, 1L)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> activityAlertService.deleteAlert(1L, 5L));
    }

    @Test
    void unsubscribe_ShouldIgnoreBlankToken() {
        assertFalse(activityAlertService.unsubscribe(" "));
        verify(activityAlertRepository, never()).deleteByUnsubscribeToken(eq(" "));
    }

}
