package com.locallife.backend.auth.api;

/**
 * DTO générique pour un message de confirmation (LL-EF-007) : utilisé
 * notamment pour la réponse de {@code /forgot-password}, volontairement
 * identique que l'email corresponde ou non à un compte existant (voir
 * {@code PasswordResetService#requestReset}).
 */
public record MessageResponse(String message) {
}
