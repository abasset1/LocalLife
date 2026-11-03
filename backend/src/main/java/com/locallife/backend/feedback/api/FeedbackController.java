package com.locallife.backend.feedback.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.feedback.application.FeedbackService;
import com.locallife.backend.feedback.application.FeedbackService.InvalidFeedbackException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Envoi d'une suggestion ou d'un signalement d'anomalie par un utilisateur connecté (connexion exigée). */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    /** {@code kind} : SUGGESTION ou BUG ; {@code pageUrl} facultatif (page d'où le message est envoyé). */
    public record FeedbackRequest(String kind, String message, String pageUrl) {
    }

    @Operation(summary = "Envoyer une suggestion ou signaler une anomalie",
            description = "Message de 5 à 2000 caractères, au plus 10 par heure et par compte. Les administrateurs "
                    + "sont prévenus par e-mail.")
    @PostMapping
    public ResponseEntity<Object> submit(@RequestBody(required = false) FeedbackRequest body,
            Authentication authentication, HttpServletRequest request) {
        if (body == null) {
            return error("Corps de requête obligatoire.", request);
        }
        try {
            FeedbackService.FeedbackView saved = service.submit(((JwtAuthentication) authentication).getUserId(),
                    body.kind(), body.message(), body.pageUrl(), request.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.id()));
        } catch (InvalidFeedbackException exception) {
            return error(exception.getMessage(), request);
        }
    }

    private static ResponseEntity<Object> error(String message, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(Instant.now(), 400, "Bad Request", message, request.getRequestURI()));
    }
}
