package com.locallife.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Diagnostic SMTP de l'administration. En test, aucun serveur SMTP n'écoute (localhost:1025) : le test d'envoi doit
 * échouer à l'étape de connexion avec une explication, sans erreur 500, et la configuration ne pas exposer de mot
 * de passe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminMailControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;

    @AfterEach
    void cleanUp() {
        if (adminId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", adminId);
        }
    }

    @Test
    void mailDiagnostics_ShouldBeAdminOnly_AndExplainAFailure() {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        adminId = jdbcTemplate.queryForObject("INSERT INTO users (username, email, role, created_at, email_verified) "
                + "VALUES (?, ?, 'ADMIN', now(), true) RETURNING id", Long.class, "mail-" + unique,
                "mail-" + unique + "@example.com");
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        client.get().uri("/api/v1/admin/mail/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "USER"))
                .exchange().expectStatus().isForbidden();
        String config = client.get().uri("/api/v1/admin/mail/config")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(adminId, "ADMIN")).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertThat(config).contains("\"host\":\"localhost\"").contains("\"warning\":\"SMTP_HOST n'est pas configuré")
                .doesNotContain("\"password\"");

        client.post().uri("/api/v1/admin/mail/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(adminId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).body("{}").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.ok").isEqualTo(false)
                .jsonPath("$.step").isEqualTo("connexion");
    }

    private String token(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claim("userId", userId).claim("email", "mail-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
