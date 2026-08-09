package com.locallife.backend.auth.api;

import com.locallife.backend.auth.application.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour les endpoints d'authentification.
 * Fournit l'endpoint POST /api/v1/auth/login pour la connexion des utilisateurs.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint pour la connexion d'un utilisateur.
     *
     * @param request la requête de login contenant email et password
     * @return une réponse contenant le token JWT si l'authentification réussit
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
