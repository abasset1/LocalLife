package com.locallife.backend.integration;

import com.locallife.backend.auth.api.AuthCookie;
import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Révocation de session côté serveur (correctif sécurité du 06/11/2026) : la déconnexion ferme LA session
 * courante (son {@code jti}) sans attendre l'expiration du jeton ni toucher aux autres sessions. Et un jeton de
 * cookie invalide/révoqué/expiré dégrade en visiteur sur une route publique au lieu de renvoyer 401. Serveur et
 * base réels.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRevocationIntegrationTest {

    @Autowired
    private TestAccounts testAccounts;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Jeton signé d'un compte partagé réel, avec un {@code jti} unique (identifiant de session). */
    private String tokenWithJti() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("userId", testAccounts.sharedAccount(Role.USER))
                .claim("email", "revocation-test@example.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private void logout(String bearer) {
        client().post().uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .exchange().expectStatus().isOk();
    }

    @Test
    void logout_ShouldRevokeThatSession_ServerSide() {
        String token = tokenWithJti();
        // Valable avant la déconnexion.
        client().get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        logout(token);

        // Le même jeton ne vaut plus rien, bien qu'il ne soit pas expiré.
        client().get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void logout_ShouldNotAffectTheUsersOtherSessions() {
        String sessionA = tokenWithJti();
        String sessionB = tokenWithJti();

        logout(sessionA);

        client().get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionA)
                .exchange().expectStatus().isUnauthorized();
        // L'autre appareil reste connecté.
        client().get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionB)
                .exchange().expectStatus().isOk();
    }

    @Test
    void invalidCookie_ShouldDegradeToVisitorOnPublicRoute_ButReject401OnProtected() {
        // Cookie illisible (ou révoqué/expiré) : la lecture publique passe en visiteur (200), pas d'erreur.
        client().get().uri("/api/v1/activities").cookie(AuthCookie.NAME, "pas-un-jeton-valide")
                .exchange().expectStatus().isOk();
        // Mais une route protégée reste refusée.
        client().get().uri("/api/v1/users/me").cookie(AuthCookie.NAME, "pas-un-jeton-valide")
                .exchange().expectStatus().isUnauthorized();
        // Le même jeton invalide dans l'en-tête Authorization (mobile/API), lui, renvoie 401 directement.
        client().get().uri("/api/v1/activities").header(HttpHeaders.AUTHORIZATION, "Bearer pas-un-jeton-valide")
                .exchange().expectStatus().isUnauthorized();
    }
}
