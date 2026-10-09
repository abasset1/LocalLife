package com.locallife.backend.user.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.user.application.UserPreferencesService;
import com.locallife.backend.user.domain.UserPreferences;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paramètres utilisateur : préférences de recherche par défaut de
 * l'utilisateur connecté (catégories, ville, rayon). Même convention
 * {@code .../me/...} que {@code UserController#getCurrentUser} : l'id
 * provient exclusivement du JWT, jamais de la requête.
 */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public UserPreferencesResponse getPreferences(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        return UserPreferencesResponse.from(userPreferencesService.getPreferences(userId));
    }

    /**
     * Remplace l'ensemble des préférences (PUT : un champ absent ou
     * {@code null} efface la préférence correspondante).
     */
    @PutMapping
    public ResponseEntity<Object> updatePreferences(
            Authentication authentication, @RequestBody UpdatePreferencesRequest request,
            HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            UserPreferences updated = userPreferencesService.updatePreferences(
                    userId, request.defaultCity(), request.defaultRadiusKm(), request.defaultCategoryCodes());
            return ResponseEntity.ok(UserPreferencesResponse.from(updated));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (AddressNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Ville introuvable : \"" + request.defaultCity() + "\".",
                    httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    public record UpdatePreferencesRequest(
            String defaultCity, Integer defaultRadiusKm, List<String> defaultCategoryCodes) {
    }

    public record UserPreferencesResponse(
            String defaultCity,
            Double defaultLatitude,
            Double defaultLongitude,
            Integer defaultRadiusKm,
            List<String> defaultCategoryCodes) {

        static UserPreferencesResponse from(UserPreferences preferences) {
            return new UserPreferencesResponse(
                    preferences.defaultCity(), preferences.defaultLatitude(), preferences.defaultLongitude(),
                    preferences.defaultRadiusKm(), preferences.defaultCategoryCodes());
        }

    }

}
