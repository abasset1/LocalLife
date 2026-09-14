package com.locallife.backend.auth.api;

/**
 * DTO pour la requête de connexion/inscription via Google (LL-EF-011).
 *
 * Un seul champ, volontairement : le token brut retourné par Google côté
 * frontend, jamais un email ou un nom fourni séparément — voir la javadoc
 * de {@code AuthController#loginWithGoogle} et de {@code GoogleAuthService}
 * pour le détail de cette exigence de sécurité.
 */
public record GoogleLoginRequest(String idToken) {
}
