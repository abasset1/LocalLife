package com.locallife.backend.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.user.api.UserPreferencesController.CityResponse;
import com.locallife.backend.user.api.UserPreferencesController.UpdatePreferencesRequest;
import com.locallife.backend.user.api.UserPreferencesController.UserPreferencesResponse;
import com.locallife.backend.user.application.UserPreferencesService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.UserPreferences;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserPreferencesControllerTest {

    @Mock
    private UserPreferencesService userPreferencesService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private UserPreferencesController userPreferencesController;

    private final JwtAuthentication authentication = new JwtAuthentication(7L, "alice@example.com", Role.USER);

    @Test
    void getPreferences_ShouldUseUserIdFromJwt() {
        when(userPreferencesService.getPreferences(7L)).thenReturn(
                new UserPreferences(7L, "Lyon", 45.76, 4.83, 20, List.of("SPORT")));

        UserPreferencesResponse response = userPreferencesController.getPreferences(authentication);

        assertEquals("Lyon", response.defaultCity());
        assertEquals(20, response.defaultRadiusKm());
        assertEquals(List.of("SPORT"), response.defaultCategoryCodes());
    }

    @Test
    void updatePreferences_ShouldReturnOk_WithSavedPreferences() {
        when(userPreferencesService.updatePreferences(7L, "Lyon", null, null, 20, List.of("SPORT"))).thenReturn(
                new UserPreferences(7L, "Lyon", 45.76, 4.83, 20, List.of("SPORT")));

        ResponseEntity<Object> response = userPreferencesController.updatePreferences(
                authentication, new UpdatePreferencesRequest("Lyon", null, null, 20, List.of("SPORT")), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(45.76, ((UserPreferencesResponse) response.getBody()).defaultLatitude());
    }

    @Test
    void updatePreferences_ShouldReturnBadRequest_WhenInvalid() {
        when(userPreferencesService.updatePreferences(7L, null, null, null, 99, null))
                .thenThrow(new IllegalArgumentException("Rayon invalide."));

        ResponseEntity<Object> response = userPreferencesController.updatePreferences(
                authentication, new UpdatePreferencesRequest(null, null, null, 99, null), httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePreferences_ShouldReturnBadRequest_WhenCityNotFound() {
        when(userPreferencesService.updatePreferences(7L, "Zzzz", null, null, null, null))
                .thenThrow(new CityNotFoundException("Ville introuvable."));

        ResponseEntity<Object> response = userPreferencesController.updatePreferences(
                authentication, new UpdatePreferencesRequest("Zzzz", null, null, null, null), httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatePreferences_ShouldReturnServiceUnavailable_WhenGeocodingDown() {
        when(userPreferencesService.updatePreferences(7L, "Lyon", null, null, null, null))
                .thenThrow(new GeocodingUnavailableException(new RuntimeException("down")));

        ResponseEntity<Object> response = userPreferencesController.updatePreferences(
                authentication, new UpdatePreferencesRequest("Lyon", null, null, null, null), httpRequest);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void lookupCity_ShouldReturnCityName() {
        when(userPreferencesService.lookupCity(null, 45.77, 4.85)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));

        ResponseEntity<Object> response = userPreferencesController.lookupCity(null, 45.77, 4.85, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new CityResponse("Lyon", 45.76, 4.83), response.getBody());
    }

    @Test
    void lookupCity_ShouldReturnBadRequest_WhenCityNotFound() {
        when(userPreferencesService.lookupCity("Zzzz", null, null))
                .thenThrow(new CityNotFoundException("Ville introuvable."));

        ResponseEntity<Object> response = userPreferencesController.lookupCity("Zzzz", null, null, httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

}
