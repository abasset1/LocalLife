package com.locallife.backend.auth.api;

/**
 * Inscription par e-mail. {@code acceptTerms} : case « J'ai au moins 15 ans et j'accepte les conditions d'utilisation
 * et la politique de confidentialité » (RGPD, 22/10/2026), obligatoire (400 sinon) ; sa date et la version des textes
 * sont enregistrées sur le compte.
 */
public record RegisterRequest(
        String username,
        String email,
        String password,
        Boolean acceptTerms) {

    /** Inscription avec la case cochée (appels Java, tests) ; un corps JSON sans {@code acceptTerms} est refusé. */
    public RegisterRequest(String username, String email, String password) {
        this(username, email, password, Boolean.TRUE);
    }
}
