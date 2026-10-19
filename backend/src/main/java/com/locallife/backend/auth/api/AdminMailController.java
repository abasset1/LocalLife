package com.locallife.backend.auth.api;

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

    /** Destinataire du test ; l'adresse de l'administrateur connecté par défaut. */
    public record TestRequest(String to) {
    }

    @Operation(summary = "Configuration SMTP effective", description = "Sans le mot de passe ; warning signale une "
            + "configuration manifestement incohérente.")
    @GetMapping("/config")
    public MailDiagnostics.MailConfig config() {
        return diagnostics.config();
    }

    @Operation(summary = "Envoyer un e-mail de test", description = "Teste la connexion SMTP puis envoie un message. "
            + "Réponse 200 dans tous les cas : ok, étape (connexion ou envoi) et explication.")
    @PostMapping("/test")
    public ResponseEntity<MailDiagnostics.MailTestResult> test(@RequestBody(required = false) TestRequest body,
            Authentication authentication) {
        String to = body != null && body.to() != null && !body.to().isBlank() ? body.to().trim()
                : userService.getUserById(((JwtAuthentication) authentication).getUserId()).orElseThrow().email();
        return ResponseEntity.ok(diagnostics.sendTest(to));
    }
}
