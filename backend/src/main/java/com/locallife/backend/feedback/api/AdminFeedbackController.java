package com.locallife.backend.feedback.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.feedback.application.FeedbackService;
import com.locallife.backend.feedback.application.FeedbackService.Status;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Suggestions et anomalies reçues, côté administration ({@code /api/v1/admin/**} : rôle ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    private static final int MAX_SIZE = 100;

    private final FeedbackService service;

    public AdminFeedbackController(FeedbackService service) {
        this.service = service;
    }

    public record UpdateFeedbackRequest(Boolean done) {
    }

    @Operation(summary = "Suggestions et anomalies reçues",
            description = "Les plus récentes d'abord ; filtre facultatif status=NEW ou DONE. newCount : messages à "
                    + "traiter, tous filtres confondus.")
    @GetMapping
    public ResponseEntity<Object> find(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            return error(HttpStatus.BAD_REQUEST, "'page' doit être positif et 'size' entre 1 et " + MAX_SIZE + ".",
                    request);
        }
        Status filter;
        try {
            filter = status == null || status.isBlank() ? null : Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "'status' doit valoir NEW ou DONE.", request);
        }
        return ResponseEntity.ok(service.find(filter, page, size));
    }

    @Operation(summary = "Marquer un message traité, ou de nouveau à traiter",
            description = "Corps : {\"done\": true}.")
    @PatchMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable Long id,
            @RequestBody(required = false) UpdateFeedbackRequest body, Authentication authentication,
            HttpServletRequest request) {
        if (body == null || body.done() == null) {
            return error(HttpStatus.BAD_REQUEST, "'done' est obligatoire.", request);
        }
        return service.setDone(id, body.done(), ((JwtAuthentication) authentication).getUserId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Message introuvable.", request));
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
