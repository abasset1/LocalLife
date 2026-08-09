package com.locallife.backend.auth.api;

/**
 * DTO pour la réponse de login.
 * Contient le token JWT généré après une authentification réussie.
 */
public record LoginResponse(
        String token) {
}
