package com.locallife.backend.geocoding.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests de GeocodingService avec un MockRestServiceServer : aucun appel
 * réseau réel vers Nominatim (ne dépend pas d'une connexion internet dans
 * l'environnement de build/CI).
 */
class GeocodingServiceTest {

    private MockRestServiceServer mockServer;
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        geocodingService = new GeocodingService(builder);
    }

    @Test
    void geocode_ShouldReturnCoordinates_WhenAddressFound() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[{\"lat\":\"43.29\",\"lon\":\"5.37\"}]", MediaType.APPLICATION_JSON));

        Coordinates coordinates = geocodingService.geocode("1 rue de la Paix, Marseille");

        assertEquals(43.29, coordinates.latitude());
        assertEquals(5.37, coordinates.longitude());
    }

    @Test
    void geocode_ShouldThrowAddressNotFoundException_WhenNoResults() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThrows(AddressNotFoundException.class, () -> geocodingService.geocode("adresse inconnue"));
    }

    @Test
    void geocode_ShouldThrowIllegalArgumentException_WhenAddressBlank() {
        assertThrows(IllegalArgumentException.class, () -> geocodingService.geocode("   "));
    }

    @Test
    void geocode_ShouldThrowIllegalArgumentException_WhenAddressNull() {
        assertThrows(IllegalArgumentException.class, () -> geocodingService.geocode(null));
    }

    @Test
    void geocode_ShouldThrowGeocodingUnavailableException_WhenServiceFails() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withServerError());

        assertThrows(GeocodingUnavailableException.class, () -> geocodingService.geocode("1 rue de la Paix"));
    }
}
