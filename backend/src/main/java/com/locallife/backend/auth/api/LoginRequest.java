package com.locallife.backend.auth.api;

/**
 * DTO pour la requête de login.
 * Contient les informations nécessaires pour authentifier un utilisateur.
 */
public record LoginRequest(
        String email,
        String password) {
}
