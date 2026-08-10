package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.AuthService;
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
 * Fournit POST /api/v1/auth/register (inscription) et POST /api/v1/auth/login (connexion).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
