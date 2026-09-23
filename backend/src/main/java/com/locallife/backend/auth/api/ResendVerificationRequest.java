package com.locallife.backend.auth.api;

/**
 * DTO pour le renvoi du lien de confirmation d'adresse email.
 */
public record ResendVerificationRequest(String email) {
}
