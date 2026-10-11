package com.locallife.backend.geocoding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class CitySearchServiceTest {

    private static final Coordinates LYON = new Coordinates(45.7578, 4.832, "Lyon", null);

    private final GeocodingService geocodingService = mock(GeocodingService.class);

    @Test
    void findCity_ShouldQueryNominatimOnce_ThenServeFromCacheWhateverTheCase() {
        CitySearchService service = new CitySearchService(geocodingService, 0);
        when(geocodingService.findCity("Lyon")).thenReturn(LYON);

        assertThat(service.findCity("Lyon")).isEqualTo(LYON);
        assertThat(service.findCity("  lyon ")).isEqualTo(LYON);

        verify(geocodingService, times(1)).findCity("Lyon");
    }

    @Test
    void findCity_ShouldCacheUnknownCities_Too() {
        CitySearchService service = new CitySearchService(geocodingService, 0);
        when(geocodingService.findCity("Zzz")).thenThrow(new CityNotFoundException("Ville introuvable"));

        assertThatThrownBy(() -> service.findCity("Zzz")).isInstanceOf(CityNotFoundException.class);
        assertThatThrownBy(() -> service.findCity("zzz")).isInstanceOf(CityNotFoundException.class)
                .hasMessage("Ville introuvable : \"zzz\".");

        verify(geocodingService, times(1)).findCity("Zzz");
    }

    @Test
    void findCity_ShouldNotCacheUnavailability() {
        CitySearchService service = new CitySearchService(geocodingService, 0);
        when(geocodingService.findCity("Lyon"))
                .thenThrow(new GeocodingUnavailableException(null))
                .thenReturn(LYON);

        assertThatThrownBy(() -> service.findCity("Lyon")).isInstanceOf(GeocodingUnavailableException.class);
        assertThat(service.findCity("Lyon")).isEqualTo(LYON);
    }

    @Test
    void findCity_ShouldSpaceNominatimCalls() {
        CitySearchService service = new CitySearchService(geocodingService, 200);
        when(geocodingService.findCity("Lyon")).thenReturn(LYON);
        when(geocodingService.findCity("Vienne")).thenReturn(new Coordinates(45.52, 4.87, "Vienne", null));

        long start = System.nanoTime();
        service.findCity("Lyon");
        service.findCity("Vienne");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(190);
    }

    @Test
    void findCity_ShouldRejectBlankName_WithoutCallingNominatim() {
        CitySearchService service = new CitySearchService(geocodingService, 0);

        assertThatThrownBy(() -> service.findCity("  ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(geocodingService);
    }
}
