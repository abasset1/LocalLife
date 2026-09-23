package com.locallife.backend.auth.api;

/**
 * DTO pour la confirmation d'adresse email : token reçu dans le lien envoyé par email.
 */
public record VerifyEmailRequest(String token) {
}
