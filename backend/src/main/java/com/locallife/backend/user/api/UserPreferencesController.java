package com.locallife.backend.user.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
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
import org.springframework.web.bind.annotation.RequestParam;
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
        return userPreferencesService.findPreferences(userId)
                .map(UserPreferencesResponse::from)
                .orElseGet(() -> UserPreferencesResponse.notConfigured(userId));
    }

    /**
     * Vérifie qu'une ville existe — par son nom ({@code name}) ou par une
     * position ({@code latitude}/{@code longitude}, géolocalisation du
     * navigateur) — et renvoie son nom officiel, sans rien enregistrer.
     * Réservé à un utilisateur connecté : chaque appel interroge Nominatim,
     * dont la politique d'usage limite le débit.
     */
    @GetMapping("/city")
    public ResponseEntity<Object> lookupCity(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            HttpServletRequest httpRequest) {
        try {
            Coordinates city = userPreferencesService.lookupCity(name, latitude, longitude);
            return ResponseEntity.ok(new CityResponse(city.city(), city.latitude(), city.longitude()));
        } catch (IllegalArgumentException | CityNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Remplace l'ensemble des préférences (PUT : un champ absent ou
     * {@code null} efface la préférence correspondante). Ville : par
     * position si {@code defaultLatitude}/{@code defaultLongitude} sont
     * fournis, sinon par {@code defaultCity} (voir
     * {@link UserPreferencesService#updatePreferences}).
     */
    @PutMapping
    public ResponseEntity<Object> updatePreferences(
            Authentication authentication, @RequestBody UpdatePreferencesRequest request,
            HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            UserPreferences updated = userPreferencesService.updatePreferences(
                    userId, request.defaultCity(), request.defaultLatitude(), request.defaultLongitude(),
                    request.defaultRadiusKm(), request.defaultCategoryCodes());
            return ResponseEntity.ok(UserPreferencesResponse.from(updated));
        } catch (IllegalArgumentException | CityNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
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
            String defaultCity, Double defaultLatitude, Double defaultLongitude, Integer defaultRadiusKm,
            List<String> defaultCategoryCodes) {
    }

    public record CityResponse(String city, Double latitude, Double longitude) {
    }

    /**
     * {@code configured} : {@code false} tant que l'utilisateur n'a jamais
     * enregistré ses préférences (première connexion) — le frontend affiche
     * alors l'accueil qui les fait choisir.
     */
    public record UserPreferencesResponse(
            String defaultCity,
            Double defaultLatitude,
            Double defaultLongitude,
            Integer defaultRadiusKm,
            List<String> defaultCategoryCodes,
            boolean configured) {

        static UserPreferencesResponse from(UserPreferences preferences) {
            return new UserPreferencesResponse(
                    preferences.defaultCity(), preferences.defaultLatitude(), preferences.defaultLongitude(),
                    preferences.defaultRadiusKm(), preferences.defaultCategoryCodes(), true);
        }

        static UserPreferencesResponse notConfigured(Long userId) {
            UserPreferences empty = UserPreferences.empty(userId);
            return new UserPreferencesResponse(
                    empty.defaultCity(), empty.defaultLatitude(), empty.defaultLongitude(), empty.defaultRadiusKm(),
                    empty.defaultCategoryCodes(), false);
        }

    }

}
