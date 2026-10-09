package com.locallife.backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.user.domain.UserPreferences;
import com.locallife.backend.user.infrastructure.UserPreferencesRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    private static Category category(String code, boolean active) {
        return new Category(1L, code, active, LocalDateTime.now(), LocalDateTime.now(), Set.of());
    }

    @Test
    void getPreferences_ShouldReturnEmpty_WhenNoneSaved() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UserPreferences result = userPreferencesService.getPreferences(1L);

        assertNull(result.defaultCity());
        assertNull(result.defaultRadiusKm());
        assertTrue(result.defaultCategoryCodes().isEmpty());
    }

    @Test
    void updatePreferences_ShouldGeocodeNewCity_AndSaveCleanedCategories() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(geocodingService.geocode("Lyon")).thenReturn(new Coordinates(45.76, 4.83, "Lyon", "69001"));
        when(categoryRepository.findAll()).thenReturn(List.of(category("SPORT", true), category("CULTURE", true)));

        UserPreferences result = userPreferencesService.updatePreferences(
                1L, "  Lyon ", 20, Arrays.asList("SPORT", " CULTURE", "SPORT", "", null));

        assertEquals("Lyon", result.defaultCity());
        assertEquals(45.76, result.defaultLatitude());
        assertEquals(4.83, result.defaultLongitude());
        assertEquals(20, result.defaultRadiusKm());
        assertEquals(List.of("CULTURE", "SPORT"), result.defaultCategoryCodes());
        verify(userPreferencesRepository).save(result);
    }

    @Test
    void updatePreferences_ShouldKeepStoredCoordinates_WhenCityUnchanged() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.of(
                new UserPreferences(1L, "Lyon", 45.76, 4.83, 10, List.of())));

        UserPreferences result = userPreferencesService.updatePreferences(1L, "lyon", 10, List.of());

        assertEquals(45.76, result.defaultLatitude());
        verify(geocodingService, never()).geocode(any());
    }

    @Test
    void updatePreferences_ShouldClearCity_WhenBlank() {
        UserPreferences result = userPreferencesService.updatePreferences(1L, "   ", null, null);

        assertNull(result.defaultCity());
        assertNull(result.defaultLatitude());
        assertNull(result.defaultLongitude());
        verify(geocodingService, never()).geocode(any());
        verify(userPreferencesRepository).save(result);
    }

    @Test
    void updatePreferences_ShouldReject_RadiusOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, 51, List.of()));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void updatePreferences_ShouldReject_UnknownOrInactiveCategory() {
        when(categoryRepository.findAll()).thenReturn(List.of(category("SPORT", true), category("OLD", false)));

        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, List.of("NOPE")));
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, List.of("OLD")));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void updatePreferences_ShouldPropagate_CityNotFound() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(geocodingService.geocode("Zzzz")).thenThrow(new AddressNotFoundException("Zzzz"));

        assertThrows(AddressNotFoundException.class,
                () -> userPreferencesService.updatePreferences(1L, "Zzzz", null, List.of()));
        verify(userPreferencesRepository, never()).save(any());
    }

}
