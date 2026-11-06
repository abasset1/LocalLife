package com.locallife.backend.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de session porteur du JWT (correctif sécurité du 06/11/2026). Le jeton n'est plus exposé au JavaScript
 * du navigateur ({@code localStorage}) : il est déposé dans un cookie {@code HttpOnly} + {@code Secure} +
 * {@code SameSite=Strict}, illisible par un script, pour qu'une éventuelle faille XSS ne puisse pas le voler et
 * usurper la session. {@code SameSite=Strict} empêche aussi le navigateur d'attacher le cookie à une requête
 * déclenchée depuis un autre site : c'est la protection CSRF de la session par cookie (voir
 * {@code SecurityConfig}).
 *
 * <p>L'application mobile et les clients qui envoient le jeton dans l'en-tête {@code Authorization: Bearer} ne
 * sont pas concernés : {@code JwtFilter} accepte les deux, et le jeton reste aussi renvoyé dans le corps de la
 * réponse de connexion pour eux.
 *
 * <p>La durée de vie du cookie suit exactement celle du jeton (lue dans son claim {@code exp} : 8 h pour un
 * administrateur, 24 h sinon, voir {@code JwtService}), sans le vérifier de nouveau — le jeton vient d'être émis
 * par le serveur. En cas de lecture impossible, repli prudent sur 24 h.
 */
@Component
public class AuthCookie {

    /** Nom du cookie de session. */
    public static final String NAME = "LL_SESSION";

    private static final Duration FALLBACK_MAX_AGE = Duration.ofHours(24);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * {@code false} seulement pour un développement local servi en clair (http) hors {@code localhost} : les
     * navigateurs acceptent déjà un cookie {@code Secure} sur {@code localhost}. Jamais {@code false} en
     * production (cookie de session en clair = vol de session sur le réseau).
     */
    private final boolean secure;

    public AuthCookie(@Value("${app.auth.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    /** Cookie à poser après une connexion réussie (ou un changement de mot de passe) : porte le jeton. */
    public ResponseCookie forToken(String token) {
        long maxAgeSeconds = Math.max(0, secondsUntilExpiry(token));
        return base(token).maxAge(maxAgeSeconds).build();
    }

    /** Cookie à poser à la déconnexion : vide et immédiatement expiré, pour effacer la session du navigateur. */
    public ResponseCookie expired() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }

    private long secondsUntilExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode exp = MAPPER.readTree(payload).get("exp");
            if (exp == null) {
                return FALLBACK_MAX_AGE.toSeconds();
            }
            return exp.asLong() - Instant.now().getEpochSecond();
        } catch (RuntimeException | java.io.IOException exception) {
            return FALLBACK_MAX_AGE.toSeconds();
        }
    }
}
