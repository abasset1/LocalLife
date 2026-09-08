package com.locallife.backend.auth.api;

/**
 * DTO pour la requête de demande de réinitialisation de mot de passe
 * (LL-EF-007).
 */
public record ForgotPasswordRequest(String email) {
}
