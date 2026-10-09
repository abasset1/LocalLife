package com.locallife.backend.push.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jetons de notification push de l'appareil courant (application mobile).
 * {@code PUT} à chaque ouverture de session ou changement de jeton,
 * {@code DELETE} à la déconnexion. L'utilisateur provient du JWT.
 */
@RestController
@RequestMapping("/api/v1/users/me/push-tokens")
public class PushTokenController {

    private static final Set<String> PLATFORMS = Set.of("ios", "android");

    private final PushTokenRepository pushTokenRepository;

    public PushTokenController(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    @PutMapping
    public ResponseEntity<Object> register(
            Authentication authentication, @RequestBody PushTokenRequest request, HttpServletRequest httpRequest) {
        if (!ExpoPushService.isExpoPushToken(request.token())) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Jeton de notification invalide.", httpRequest);
        }
        String platform = request.platform() == null || !PLATFORMS.contains(request.platform())
                ? null : request.platform();
        Long userId = ((JwtAuthentication) authentication).getUserId();
        pushTokenRepository.save(request.token(), userId, platform);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Object> unregister(Authentication authentication, @RequestBody PushTokenRequest request) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        if (request.token() != null) {
            pushTokenRepository.deleteByTokenAndUserId(request.token(), userId);
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    public record PushTokenRequest(String token, String platform) {
    }

}
