package com.locallife.backend.alert.api;

import com.locallife.backend.alert.application.ActivityAlertService;
import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertes email. {@code /users/me/alerts} : alertes de l'utilisateur
 * connecté, id lu dans le JWT (même convention que
 * {@code UserController#getCurrentUser}). {@code /alerts/unsubscribe} :
 * public, appelé depuis le lien de l'email — le jeton suffit à supprimer
 * l'alerte concernée, et rien d'autre.
 */
@RestController
public class ActivityAlertController {

    private final ActivityAlertService activityAlertService;

    public ActivityAlertController(ActivityAlertService activityAlertService) {
        this.activityAlertService = activityAlertService;
    }

    @GetMapping("/api/v1/users/me/alerts")
    public List<AlertResponse> getAlerts(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        return activityAlertService.getAlerts(userId).stream().map(AlertResponse::from).toList();
    }

    @PostMapping("/api/v1/users/me/alerts")
    public ResponseEntity<Object> createAlert(
            Authentication authentication, @RequestBody CreateAlertRequest request, HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            ActivityAlert created = activityAlertService.createAlert(
                    userId, request.categoryCodes(), request.city(), request.latitude(), request.longitude(),
                    request.radiusKm(), request.notifyEmail(), request.notifyPush());
            return ResponseEntity.status(HttpStatus.CREATED).body(AlertResponse.from(created));
        } catch (IllegalArgumentException | CityNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    /** Change les canaux d'une alerte (e-mail / notification). */
    @PatchMapping("/api/v1/users/me/alerts/{id}")
    public ResponseEntity<Object> updateChannels(
            Authentication authentication, @PathVariable Long id, @RequestBody UpdateChannelsRequest request,
            HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            ActivityAlert updated = activityAlertService.updateChannels(
                    userId, id, Boolean.TRUE.equals(request.notifyEmail()), Boolean.TRUE.equals(request.notifyPush()));
            return ResponseEntity.ok(AlertResponse.from(updated));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (NoSuchElementException exception) {
            return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), httpRequest);
        }
    }

    @DeleteMapping("/api/v1/users/me/alerts/{id}")
    public ResponseEntity<Object> deleteAlert(
            Authentication authentication, @PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            activityAlertService.deleteAlert(userId, id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException exception) {
            return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), httpRequest);
        }
    }

    @PostMapping("/api/v1/alerts/unsubscribe")
    public ResponseEntity<Object> unsubscribe(@RequestBody UnsubscribeRequest request, HttpServletRequest httpRequest) {
        if (activityAlertService.unsubscribe(request.token())) {
            return ResponseEntity.noContent().build();
        }
        return errorResponse(HttpStatus.NOT_FOUND,
                "Ce lien de désinscription n'est plus valide : l'alerte a peut-être déjà été supprimée.", httpRequest);
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    public record CreateAlertRequest(
            List<String> categoryCodes, String city, Double latitude, Double longitude, Integer radiusKm,
            Boolean notifyEmail, Boolean notifyPush) {
    }

    public record UpdateChannelsRequest(Boolean notifyEmail, Boolean notifyPush) {
    }

    public record UnsubscribeRequest(String token) {
    }

    public record AlertResponse(
            Long id, List<String> categoryCodes, String city, double latitude, double longitude, int radiusKm,
            boolean notifyEmail, boolean notifyPush) {

        static AlertResponse from(ActivityAlert alert) {
            return new AlertResponse(alert.id(), alert.categoryCodes(), alert.city(), alert.latitude(),
                    alert.longitude(), alert.radiusKm(), alert.notifyEmail(), alert.notifyPush());
        }

    }

}
