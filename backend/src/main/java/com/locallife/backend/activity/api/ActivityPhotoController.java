package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityPhotoService;
import com.locallife.backend.activity.application.ContributionAccessPolicy;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Image d'une activité ajoutée par son contributeur (formulaire « Proposer une activité », site et application).
 * Elle devient l'image principale de l'activité ({@code mainImageUrl}) et son adresse figure dans
 * {@link ContributionResponse#photoUrl()}.
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityPhotoController {

    /** Corps de {@code PUT /{id}/photo} : image JPEG, PNG ou WebP en base64 (adresse {@code data:} acceptée). */
    public record PhotoInput(String data) {
    }

    private final ActivityPhotoService photoService;
    private final ContributionAccessPolicy contributionAccessPolicy;

    public ActivityPhotoController(ActivityPhotoService photoService,
            ContributionAccessPolicy contributionAccessPolicy) {
        this.photoService = photoService;
        this.contributionAccessPolicy = contributionAccessPolicy;
    }

    @Operation(summary = "Image d'une activité",
            description = "JPEG, PNG ou WebP ; 404 sans image. Celle d'une activité en attente ou refusée n'est "
                    + "servie qu'à son créateur et à l'équipe.")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id, Authentication authentication) {
        Long requesterId = authentication instanceof JwtAuthentication jwt ? jwt.getUserId() : null;
        Role role = authentication instanceof JwtAuthentication jwt ? jwt.getRole() : null;
        return photoService.findVisible(id, requesterId, role)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        // L'adresse change à chaque nouvelle image (?v=…) ; privé : une image en attente de
                        // validation ne doit pas rester dans un cache partagé.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(photo.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Ajouter ou remplacer l'image d'une contribution",
            description = "Corps : {\"data\": \"<image JPEG, PNG ou WebP en base64>\"} (3 Mo au plus). Réservé au "
                    + "créateur (offre Premium ou Pro) ou à un administrateur. Une activité publiée ou refusée "
                    + "repasse en attente de validation.")
    @PutMapping("/{id}/photo")
    public ResponseEntity<Object> savePhoto(@PathVariable Long id, @RequestBody(required = false) PhotoInput body,
            Authentication authentication, HttpServletRequest request) {
        JwtAuthentication user = (JwtAuthentication) authentication;
        if (!contributionAccessPolicy.canContribute(user.getUserId())) {
            return error(HttpStatus.FORBIDDEN, ContributionAccessPolicy.DENIED_MESSAGE, request);
        }
        try {
            boolean found = photoService.save(id, user.getUserId(), user.getRole() == Role.ADMIN,
                    body == null ? null : body.data());
            return found ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
        } catch (ActivityAccessDeniedException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
        }
    }

    @Operation(summary = "Retirer l'image d'une contribution")
    @DeleteMapping("/{id}/photo")
    public ResponseEntity<Object> deletePhoto(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        JwtAuthentication user = (JwtAuthentication) authentication;
        try {
            boolean found = photoService.delete(id, user.getUserId(), user.getRole() == Role.ADMIN);
            return found ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
        } catch (ActivityAccessDeniedException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
        }
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
