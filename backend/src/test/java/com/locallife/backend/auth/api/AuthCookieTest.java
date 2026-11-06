package com.locallife.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * Attributs du cookie de session (correctif sécurité du 06/11/2026) : {@code HttpOnly} + {@code Secure} +
 * {@code SameSite=Strict}, durée alignée sur l'expiration du jeton. Pas de contexte Spring : la classe ne lit
 * que son propre paramètre {@code secure} et le claim {@code exp} du jeton (base64), sans vérifier la signature.
 */
class AuthCookieTest {

    /** Jeton minimal {@code header.payload.sig} dont le seul claim utile est {@code exp}. */
    private static String tokenExpiringInSeconds(long seconds) {
        long exp = Instant.now().getEpochSecond() + seconds;
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        return "header." + payload + ".signature";
    }

    @Test
    void forToken_ShouldBeHttpOnlySecureStrict_AndCarryTheToken() {
        AuthCookie authCookie = new AuthCookie(true);
        String token = tokenExpiringInSeconds(3600);

        ResponseCookie cookie = authCookie.forToken(token);

        assertThat(cookie.getName()).isEqualTo(AuthCookie.NAME);
        assertThat(cookie.getValue()).isEqualTo(token);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        // Durée alignée sur l'expiration du jeton (tolérance pour le temps écoulé pendant le test).
        assertThat(cookie.getMaxAge().getSeconds()).isBetween(3590L, 3600L);
    }

    @Test
    void forToken_WhenCookieNotSecure_ShouldDropSecureFlagOnly() {
        ResponseCookie cookie = new AuthCookie(false).forToken(tokenExpiringInSeconds(3600));

        assertThat(cookie.isSecure()).isFalse();
        // Les autres protections restent, Secure mis à part (développement local en clair).
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void forToken_WhenExpiryUnreadable_ShouldFallBackToTwentyFourHours() {
        ResponseCookie cookie = new AuthCookie(true).forToken("not-a-valid-jwt");

        assertThat(cookie.getMaxAge().toHours()).isEqualTo(24);
    }

    @Test
    void expired_ShouldClearTheCookie() {
        ResponseCookie cookie = new AuthCookie(true).expired();

        assertThat(cookie.getName()).isEqualTo(AuthCookie.NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
    }
}
