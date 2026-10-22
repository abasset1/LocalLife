package com.locallife.backend.auth.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.http.HttpStatus;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.auth.application.MailDiagnostics;
import com.locallife.backend.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic de l'envoi d'e-mails (administration) : configuration SMTP effective, sans le mot de passe, et envoi
 * d'un e-mail de test avec l'explication d'un échec. Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/mail")
public class AdminMailController {

    private final MailDiagnostics diagnostics;
    private final UserService userService;

    public AdminMailController(MailDiagnostics diagnostics, UserService userService) {
        this.diagnostics = diagnostics;
        this.userService = userService;
    }

    /** Ancien corps ({@code to}) : ignoré depuis le 22/10/2026, le test part vers l'administrateur connecté. */
    public record TestRequest(String to) {
    }

    /** Au plus 5 tests par heure et par administrateur. */
    static final int MAX_TESTS_PER_HOUR = 5;

    private final Map<Long, Deque<Instant>> recentTests =
            new ConcurrentHashMap<>();

    @Operation(summary = "Configuration SMTP effective", description = "Sans le mot de passe ; warning signale une "
            + "configuration manifestement incohérente.")
    @GetMapping("/config")
    public MailDiagnostics.MailConfig config() {
        return diagnostics.config();
    }

    @Operation(summary = "Envoyer un e-mail de test", description = "Teste la connexion SMTP puis envoie un message "
            + "à l'adresse de l'administrateur connecté uniquement (22/10/2026), 5 fois par heure au plus (429). "
            + "Réponse 200 sinon : ok, étape (connexion ou envoi) et explication.")
    @PostMapping("/test")
    public ResponseEntity<Object> test(@RequestBody(required = false) TestRequest body,
            Authentication authentication) {
        Long adminId = ((JwtAuthentication) authentication).getUserId();
        Instant now = Instant.now();
        Deque<Instant> sent = recentTests.computeIfAbsent(adminId,
                id -> new ConcurrentLinkedDeque<>());
        synchronized (sent) {
            sent.removeIf(at -> at.isBefore(now.minus(Duration.ofHours(1))));
            if (sent.size() >= MAX_TESTS_PER_HOUR) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "Au plus " + MAX_TESTS_PER_HOUR
                                + " e-mails de test par heure : réessaie plus tard."));
            }
            sent.add(now);
        }
        String to = userService.getUserById(adminId).orElseThrow().email();
        return ResponseEntity.ok(diagnostics.sendTest(to));
    }
}
