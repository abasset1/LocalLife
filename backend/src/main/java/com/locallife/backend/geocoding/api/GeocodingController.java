package com.locallife.backend.geocoding.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.CitySearchService;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche de ville publique, utilisée par la barre de recherche de la carte pour s'y déplacer
 * (visiteur non connecté compris). Débit vers Nominatim encadré par {@link CitySearchService}.
 */
@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {

    private final CitySearchService citySearchService;

    public GeocodingController(CitySearchService citySearchService) {
        this.citySearchService = citySearchService;
    }

    /**
     * {@code GET /api/v1/geocoding/city?name=…} : nom officiel et position de la ville. 400 si le
     * nom est vide, 404 si aucune ville ne correspond, 503 si Nominatim est indisponible ou saturé.
     */
    @GetMapping("/city")
    public ResponseEntity<Object> findCity(@RequestParam("name") String name, HttpServletRequest httpRequest) {
        try {
            Coordinates city = citySearchService.findCity(name);
            return ResponseEntity.ok(new CityResponse(city.city(), city.latitude(), city.longitude()));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (CityNotFoundException exception) {
            return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Ville trouvée : nom officiel et position de son centre. */
    public record CityResponse(String city, double latitude, double longitude) {
    }
}
