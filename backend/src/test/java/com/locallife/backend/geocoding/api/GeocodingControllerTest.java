package com.locallife.backend.geocoding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.CitySearchService;
import com.locallife.backend.geocoding.application.CitySuggestionService;
import com.locallife.backend.geocoding.application.CitySuggestionService.CitySuggestion;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GeocodingControllerTest {

    private final CitySearchService citySearchService = mock(CitySearchService.class);
    private final CitySuggestionService citySuggestionService = mock(CitySuggestionService.class);
    private final GeocodingController controller = new GeocodingController(citySearchService, citySuggestionService);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/geocoding/city");

    @Test
    void findCity_ShouldReturnCity() {
        when(citySearchService.findCity("lyon")).thenReturn(new Coordinates(45.75, 4.83, "Lyon", "69001"));

        var response = controller.findCity("lyon", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new GeocodingController.CityResponse("Lyon", 45.75, 4.83));
    }

    @Test
    void findCity_ShouldMapErrorsToStatuses() {
        when(citySearchService.findCity(" ")).thenThrow(new IllegalArgumentException("vide"));
        when(citySearchService.findCity("zzz")).thenThrow(new CityNotFoundException("introuvable"));
        when(citySearchService.findCity("lyon")).thenThrow(new GeocodingUnavailableException(null));

        assertThat(controller.findCity(" ", request).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.findCity("zzz", request).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.findCity("lyon", request).getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void suggestCities_ShouldReturnSuggestions_Or503() {
        CitySuggestion lyon = new CitySuggestion("Lyon", List.of("69001", "69002"), "69", 45.75, 4.83);
        when(citySuggestionService.suggest("lyo")).thenReturn(List.of(lyon));
        when(citySuggestionService.suggest("par")).thenThrow(new GeocodingUnavailableException(null));

        var ok = controller.suggestCities("lyo", request);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody()).isEqualTo(List.of(lyon));
        assertThat(controller.suggestCities("par", request).getStatusCode().value()).isEqualTo(503);
    }
}
