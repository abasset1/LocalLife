package com.locallife.backend.auth.api;

/**
 * DTO pour la requête de connexion/inscription via Google (LL-EF-011).
 *
 * Le token brut retourné par Google côté frontend, jamais un email ou un nom fourni séparément — voir la javadoc
 * de {@code AuthController#loginWithGoogle} et de {@code GoogleAuthService} pour le détail de cette exigence de
 * sécurité. {@code acceptTerms} (RGPD, 22/10/2026) : nécessaire seulement si la connexion crée un compte (428 sinon).
 */
public record GoogleLoginRequest(String idToken, Boolean acceptTerms) {

    /** Connexion à un compte existant. */
    public GoogleLoginRequest(String idToken) {
        this(idToken, null);
    }
}
