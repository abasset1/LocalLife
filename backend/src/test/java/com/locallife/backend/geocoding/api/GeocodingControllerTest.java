package com.locallife.backend.geocoding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.CitySearchService;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GeocodingControllerTest {

    private final CitySearchService citySearchService = mock(CitySearchService.class);
    private final GeocodingController controller = new GeocodingController(citySearchService);
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
}
