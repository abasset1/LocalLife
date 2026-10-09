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
import com.locallife.backend.geocoding.application.CityNotFoundException;
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
    void updatePreferences_ShouldStoreOfficialCityName_AndSaveCleanedCategories() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(geocodingService.findCity("lyon")).thenReturn(new Coordinates(45.76, 4.83, "Lyon", "69001"));
        when(categoryRepository.findAll()).thenReturn(List.of(category("SPORT", true), category("CULTURE", true)));

        UserPreferences result = userPreferencesService.updatePreferences(
                1L, "  lyon ", null, null, 20, Arrays.asList("SPORT", " CULTURE", "SPORT", "", null));

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

        UserPreferences result = userPreferencesService.updatePreferences(1L, "lyon", null, null, 10, List.of());

        assertEquals("Lyon", result.defaultCity());
        assertEquals(45.76, result.defaultLatitude());
        verify(geocodingService, never()).findCity(any());
    }

    @Test
    void updatePreferences_ShouldClearCity_WhenBlank() {
        UserPreferences result = userPreferencesService.updatePreferences(1L, "   ", null, null, null, null);

        assertNull(result.defaultCity());
        assertNull(result.defaultLatitude());
        assertNull(result.defaultLongitude());
        verify(geocodingService, never()).findCity(any());
        verify(userPreferencesRepository).save(result);
    }

    @Test
    void updatePreferences_ShouldReject_RadiusOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, null, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, null, 51, List.of()));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void updatePreferences_ShouldReject_UnknownOrInactiveCategory() {
        when(categoryRepository.findAll()).thenReturn(List.of(category("SPORT", true), category("OLD", false)));

        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, null, null, List.of("NOPE")));
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, null, null, null, List.of("OLD")));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void updatePreferences_ShouldPropagate_CityNotFound() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(geocodingService.findCity("Zzzz")).thenThrow(new CityNotFoundException("Ville introuvable."));

        assertThrows(CityNotFoundException.class,
                () -> userPreferencesService.updatePreferences(1L, "Zzzz", null, null, null, List.of()));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void updatePreferences_ShouldUseCityAtPosition_WhenPositionProvided() {
        when(geocodingService.reverseCity(45.77, 4.85)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));

        // Le nom saisi est ignoré : la position (géolocalisation) est prioritaire.
        UserPreferences result = userPreferencesService.updatePreferences(
                1L, "Paris", 45.77, 4.85, null, List.of());

        assertEquals("Lyon", result.defaultCity());
        assertEquals(45.76, result.defaultLatitude());
        assertEquals(4.83, result.defaultLongitude());
        verify(geocodingService, never()).findCity(any());
        verify(userPreferencesRepository).save(result);
    }

    @Test
    void updatePreferences_ShouldReject_IncompletePosition() {
        assertThrows(IllegalArgumentException.class,
                () -> userPreferencesService.updatePreferences(1L, null, 45.77, null, null, List.of()));
        verify(userPreferencesRepository, never()).save(any());
    }

    @Test
    void lookupCity_ShouldSearchByName_WhenNoPosition() {
        when(geocodingService.findCity("Lyon")).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));

        assertEquals("Lyon", userPreferencesService.lookupCity("Lyon", null, null).city());
    }

}
