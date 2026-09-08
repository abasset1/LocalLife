package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.AuthService;
import com.locallife.backend.auth.application.PasswordResetService;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour les endpoints d'authentification.
 * Fournit POST /api/v1/auth/register (inscription), POST /api/v1/auth/login
 * (connexion) et, depuis LL-EF-007, POST /api/v1/auth/forgot-password et
 * POST /api/v1/auth/reset-password (réinitialisation de mot de passe).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
            "Si un compte existe pour cette adresse, un email de réinitialisation vient d'être envoyé.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Endpoint pour l'inscription d'un nouvel utilisateur.
     *
     * @param request la requête d'inscription (username, email, password)
     * @return l'utilisateur créé (sans passwordHash) si l'inscription réussit
     */
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            User user = authService.register(request.username(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour la connexion d'un utilisateur.
     *
     * @param request la requête de login contenant email et password
     * @return une réponse contenant le token JWT si l'authentification réussit
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return unauthorized(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour demander une réinitialisation de mot de passe.
     *
     * <p>Renvoie systématiquement 200 avec le même message générique, que
     * l'email corresponde ou non à un compte existant (LL-EF-007 : pas
     * d'énumération des comptes). Seul un email malformé/absent renvoie
     * 400, ce qui ne constitue pas une fuite d'existence de compte.
     *
     * @param request la requête contenant l'email
     * @return un message générique de confirmation
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Object> forgotPassword(
            @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            passwordResetService.requestReset(request.email());
            return ResponseEntity.ok(new MessageResponse(GENERIC_FORGOT_PASSWORD_MESSAGE));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    /**
     * Endpoint pour définir un nouveau mot de passe à partir d'un token de
     * réinitialisation reçu par email.
     *
     * @param request la requête contenant le token et le nouveau mot de passe
     * @return un message de confirmation si la réinitialisation réussit
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Object> resetPassword(
            @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            passwordResetService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(new MessageResponse("Mot de passe mis à jour avec succès."));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception, httpRequest);
        }
    }

    private ResponseEntity<Object> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(exception, status, request));
    }

    private ResponseEntity<Object> unauthorized(IllegalArgumentException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(errorBody(exception, status, request));
    }

    private ErrorResponse errorBody(IllegalArgumentException exception, HttpStatus status, HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI());
    }
}
