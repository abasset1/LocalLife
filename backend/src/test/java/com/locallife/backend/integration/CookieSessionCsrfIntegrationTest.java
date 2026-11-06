package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.api.AuthCookie;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Correctif sécurité du 06/11/2026 : session web portée par un cookie {@code HttpOnly} au lieu du
 * {@code localStorage}, et protection CSRF ciblée sur l'authentification par cookie.
 *
 * <p>Vérifie de bout en bout (serveur réel, base réelle) que :
 * <ul>
 *   <li>{@code JwtFilter} authentifie une requête dont le jeton est dans le cookie {@code LL_SESSION} (et pas
 *       seulement dans l'en-tête {@code Authorization}) ;</li>
 *   <li>la connexion dépose un cookie {@code LL_SESSION} {@code HttpOnly}/{@code Strict} ;</li>
 *   <li>une écriture authentifiée par cookie sans jeton CSRF est refusée (403), et acceptée avec le jeton ;</li>
 *   <li>une écriture authentifiée par en-tête {@code Bearer} (mobile, API) n'exige aucun jeton CSRF ;</li>
 *   <li>la déconnexion efface le cookie de session.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookieSessionCsrfIntegrationTest {

    /** Adresse du compte administrateur partagé (voir {@link TestAccounts#sharedAccount}). */
    private static final String ADMIN_EMAIL = "it-admin@locallife.test";

    @Autowired
    private TestAccounts testAccounts;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Jeton signé d'un compte partagé réel (le rôle est relu en base par {@code JwtFilter}). */
    private String token(Role role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", testAccounts.sharedAccount(role))
                .claim("email", "cookie-csrf-test@example.com")
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void cookieAuthentication_ShouldAuthenticateAProtectedRequest() {
        client().get().uri("/api/v1/users/me")
                .cookie(AuthCookie.NAME, token(Role.USER))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void login_ShouldSetAnHttpOnlyStrictSessionCookie() {
        testAccounts.sharedAccount(Role.ADMIN); // garantit l'existence du compte et de son mot de passe

        ResponseCookie session = client().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(ADMIN_EMAIL, TestAccounts.ADMIN_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst(AuthCookie.NAME);

        assertThat(session).isNotNull();
        assertThat(session.getValue()).isNotBlank();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void cookieWrite_WithoutCsrfToken_ShouldBeForbidden() {
        client().post().uri("/api/v1/users/me/password")
                .cookie(AuthCookie.NAME, token(Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void cookieWrite_WithCsrfToken_ShouldReachTheController() {
        String jwt = token(Role.USER);
        // Un GET matérialise le cookie XSRF-TOKEN (voir CsrfCookieFilter).
        ResponseCookie xsrf = client().get().uri("/api/v1/users/me")
                .cookie(AuthCookie.NAME, jwt)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        // Cookie de session + cookie et en-tête CSRF : la requête n'est plus bloquée par le CSRF (atteint le
        // contrôleur, qui répond 400 sur ce corps vide — l'essentiel est que ce ne soit pas 403).
        client().post().uri("/api/v1/users/me/password")
                .cookie(AuthCookie.NAME, jwt)
                .cookie("XSRF-TOKEN", xsrf.getValue())
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(403));
    }

    @Test
    void bearerWrite_ShouldNotRequireCsrf() {
        client().post().uri("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(403));
    }

    @Test
    void logout_ShouldClearTheSessionCookie() {
        ResponseCookie cleared = client().post().uri("/api/v1/auth/logout")
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst(AuthCookie.NAME);

        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge().isZero()).isTrue();
    }
}
