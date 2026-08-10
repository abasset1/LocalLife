package com.locallife.backend.auth.api;

/**
 * DTO pour la requête d'inscription.
 * Contient les informations nécessaires pour créer un nouveau compte.
 */
public record RegisterRequest(
        String username,
        String email,
        String password) {
}
