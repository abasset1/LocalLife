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
    void geocode_ShouldReturnCityAndPostalCode_FromAddressDetails() {
        // LL-EF-008 : addressdetails=1 (voir GeocodingService) renvoie un
        // sous-objet "address" avec city/town/village + postcode.
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(
                        "[{\"lat\":\"43.29\",\"lon\":\"5.37\","
                                + "\"address\":{\"city\":\"Marseille\",\"postcode\":\"13001\"}}]",
                        MediaType.APPLICATION_JSON));

        Coordinates coordinates = geocodingService.geocode("1 rue de la Paix, Marseille");

        assertEquals("Marseille", coordinates.city());
        assertEquals("13001", coordinates.postalCode());
    }

    @Test
    void geocode_ShouldFallBackToTownThenVillage_WhenCityAbsent() {
        // Nominatim n'a pas de champ "ville" unique (voir GeocodingService).
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(
                        "[{\"lat\":\"43.29\",\"lon\":\"5.37\",\"address\":{\"town\":\"Cassis\"}}]",
                        MediaType.APPLICATION_JSON));

        Coordinates coordinates = geocodingService.geocode("Cassis");

        assertEquals("Cassis", coordinates.city());
    }

    @Test
    void geocode_ShouldReturnNullCityAndPostalCode_WhenAddressDetailsAbsent() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[{\"lat\":\"43.29\",\"lon\":\"5.37\"}]", MediaType.APPLICATION_JSON));

        Coordinates coordinates = geocodingService.geocode("1 rue de la Paix, Marseille");

        assertEquals(null, coordinates.city());
        assertEquals(null, coordinates.postalCode());
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

    @Test
    void findCity_ShouldRestrictToCities_AndReturnOfficialName() {
        mockServer.expect(requestTo(containsString("featureType=city")))
                .andRespond(withSuccess(
                        "[{\"lat\":\"45.76\",\"lon\":\"4.83\",\"address\":{\"city\":\"Lyon\"}}]",
                        MediaType.APPLICATION_JSON));

        Coordinates city = geocodingService.findCity("lyon");

        assertEquals("Lyon", city.city());
        assertEquals(45.76, city.latitude());
    }

    @Test
    void findCity_ShouldThrowCityNotFound_WhenNoResults() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThrows(CityNotFoundException.class, () -> geocodingService.findCity("Zzzz"));
    }

    @Test
    void findCity_ShouldThrowCityNotFound_WhenResultHasNoLocality() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(
                        "[{\"lat\":\"45.0\",\"lon\":\"4.0\",\"address\":{\"state\":\"Rhône\"}}]",
                        MediaType.APPLICATION_JSON));

        assertThrows(CityNotFoundException.class, () -> geocodingService.findCity("Rhône"));
    }

    @Test
    void reverseCity_ShouldReturnCityAtPosition() {
        mockServer.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(
                        "{\"lat\":\"43.21\",\"lon\":\"5.54\",\"address\":{\"town\":\"Cassis\"}}",
                        MediaType.APPLICATION_JSON));

        Coordinates city = geocodingService.reverseCity(43.2148, 5.5376);

        assertEquals("Cassis", city.city());
        assertEquals(43.21, city.latitude());
    }

    @Test
    void reverseCity_ShouldThrowCityNotFound_WhenNominatimReturnsError() {
        mockServer.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess("{\"error\":\"Unable to geocode\"}", MediaType.APPLICATION_JSON));

        assertThrows(CityNotFoundException.class, () -> geocodingService.reverseCity(0.0, -30.0));
    }

    @Test
    void reverseCity_ShouldRejectOutOfRangePosition() {
        assertThrows(IllegalArgumentException.class, () -> geocodingService.reverseCity(91.0, 0.0));
    }

    @Test
    void resolveCity_ShouldRejectIncompletePosition() {
        assertThrows(IllegalArgumentException.class, () -> geocodingService.resolveCity(null, 45.0, null));
    }

    @Test
    void resolveCity_ShouldPreferPosition_OverName() {
        mockServer.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(
                        "{\"lat\":\"43.21\",\"lon\":\"5.54\",\"address\":{\"town\":\"Cassis\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("Cassis", geocodingService.resolveCity("Paris", 43.21, 5.54).city());
    }
}
