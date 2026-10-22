package com.locallife.backend.gdpr;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.gdpr.application.LegalTerms;
import com.locallife.backend.gdpr.application.RetentionService;
import com.locallife.backend.user.application.PasswordHashingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * RGPD (décisions du 22/10/2026, {@code docs/02_Architecture/RGPD.md}) : consentement à l'inscription, export des
 * données, effacement des adresses dans les journaux à la suppression d'un compte, durées de conservation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GdprIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PASSWORD = "motDePasseRgpd1";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashingService passwordHashingService;

    @Autowired
    private RetentionService retentionService;

    @MockitoBean
    private EmailService emailService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        emails.forEach(email -> jdbcTemplate.update("DELETE FROM users WHERE email = ?", email));
    }

    @Test
    void registration_ShouldRequireTheConsentBox_AndRecordItsVersion() throws Exception {
        String email = "rgpd-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        Map<String, Object> body = Map.of("username", "rgpd-" + UUID.randomUUID().toString().substring(0, 8),
                "email", email, "password", PASSWORD);
        JsonNode refused = call(HttpMethod.POST, "/api/v1/auth/register", null, body, 400);
        assertThat(refused.get("message").asText()).contains("15 ans");
        Map<String, Object> accepted = new java.util.HashMap<>(body);
        accepted.put("acceptTerms", true);
        call(HttpMethod.POST, "/api/v1/auth/register", null, accepted, 201);
        assertThat(jdbcTemplate.queryForObject("SELECT terms_version FROM users WHERE email = ?", String.class,
                email)).isEqualTo(LegalTerms.VERSION);
        assertThat(jdbcTemplate.queryForObject("SELECT terms_accepted_at IS NOT NULL FROM users WHERE email = ?",
                Boolean.class, email)).isTrue();
    }

    @Test
    void export_ShouldContainTheAccountData_WithoutAnySecret() throws Exception {
        long userId = user("USER", true);
        jdbcTemplate.update("INSERT INTO activity_alert (user_id, city, latitude, longitude, radius_km, "
                + "min_activity_id, unsubscribe_token) VALUES (?, 'Lyon', 45.76, 4.83, 10, 0, ?)", userId,
                "secret-" + UUID.randomUUID());
        String body = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build().get()
                .uri("/api/v1/users/me/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId))
                .exchange().expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_DISPOSITION, "attachment.*")
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode export = OBJECT_MAPPER.readTree(body);
        assertThat(export.get("account").get("email").asText()).startsWith("rg-");
        assertThat(export.get("alerts").get(0).get("city").asText()).isEqualTo("Lyon");
        assertThat(body).doesNotContain("password_hash").doesNotContain("secret-").doesNotContain("$2a$");
        call(HttpMethod.GET, "/api/v1/users/me/export", null, null, 401);
    }

    @Test
    void accountDeletion_ShouldEraseTheEmailFromTheLogs() throws Exception {
        long userId = user("USER", true);
        String email = jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
        // Le changement de mot de passe ferme les autres sessions et renvoie un nouveau jeton.
        String newToken = call(HttpMethod.POST, "/api/v1/users/me/password", token(userId),
                Map.of("currentPassword", PASSWORD, "newPassword", "nouveauMotDePasse1"), 200).get("token").asText();
        assertThat(count("SELECT count(*) FROM account_audit_event WHERE actor_email = ?", email)).isEqualTo(1);

        call(HttpMethod.POST, "/api/v1/users/me/deletion", newToken, Map.of("password", "nouveauMotDePasse1"),
                204);
        assertThat(count("SELECT count(*) FROM account_audit_event WHERE actor_email = ?", email)).isZero();
        assertThat(count("SELECT count(*) FROM account_audit_event WHERE actor_user_id = ? "
                + "AND action = 'PASSWORD_CHANGE'", userId)).isEqualTo(1);
        // Hors de cet effacement, le journal reste en ajout seul.
        try {
            jdbcTemplate.update("UPDATE account_audit_event SET reason = 'x' WHERE actor_user_id = ?", userId);
            throw new AssertionError("Le journal ne doit pas être modifiable");
        } catch (org.springframework.dao.DataAccessException expected) {
            assertThat(expected.getMessage()).contains("ajout seul");
        }
    }

    @Test
    void retention_ShouldPurgeExpiredData_AndDeleteInactiveAccountsAfterAWarning() throws Exception {
        long unverified = user("USER", false);
        jdbcTemplate.update("UPDATE users SET created_at = now() - interval '31 days' WHERE id = ?", unverified);
        long recentUnverified = user("USER", false);
        long inactive = user("USER", true);
        jdbcTemplate.update("UPDATE users SET last_active_at = now() - interval '4 years' WHERE id = ?", inactive);
        long active = user("USER", true);
        jdbcTemplate.update("INSERT INTO password_reset_token (user_id, token_hash, expires_at) "
                + "VALUES (?, ?, now() - interval '10 days')", active, UUID.randomUUID().toString());

        Map<String, Integer> first = retentionService.applyAll();
        assertThat(first.values()).doesNotContain(-1);
        assertThat(exists(unverified)).isFalse();
        assertThat(exists(recentUnverified)).isTrue();
        assertThat(count("SELECT count(*) FROM password_reset_token WHERE user_id = ?", active)).isZero();
        // Compte inactif : averti, pas encore supprimé.
        assertThat(exists(inactive)).isTrue();
        assertThat(count("SELECT count(*) FROM users WHERE id = ? AND inactivity_warned_at IS NOT NULL", inactive))
                .isEqualTo(1);

        // 30 jours plus tard, toujours sans activité : supprimé.
        jdbcTemplate.update("UPDATE users SET inactivity_warned_at = now() - interval '31 days' WHERE id = ?",
                inactive);
        retentionService.applyAll();
        assertThat(exists(inactive)).isFalse();
        assertThat(exists(active)).isTrue();
    }

    @Test
    void anActiveRequest_ShouldResetTheInactivityWarning() throws Exception {
        long userId = user("USER", true);
        jdbcTemplate.update("UPDATE users SET last_active_at = now() - interval '4 years', "
                + "inactivity_warned_at = now() - interval '5 days' WHERE id = ?", userId);
        call(HttpMethod.GET, "/api/v1/users/me", token(userId), null, 200);
        assertThat(count("SELECT count(*) FROM users WHERE id = ? AND inactivity_warned_at IS NULL "
                + "AND last_active_at > now() - interval '1 minute'", userId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- outils

    private boolean exists(long userId) {
        return count("SELECT count(*) FROM users WHERE id = ?", userId) == 1;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(OBJECT_MAPPER.writeValueAsString(body));
        }
        String response = spec.exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : OBJECT_MAPPER.readTree(response);
    }

    private long user(String role, boolean verified) {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, password_hash, role, created_at, "
                + "email_verified, last_active_at) VALUES (?, ?, ?, ?, now(), ?, now()) RETURNING id", Long.class,
                "rg-" + unique, "rg-" + unique + "@example.com", passwordHashingService.hash(PASSWORD), role,
                verified);
        userIds.add(id);
        return id;
    }

    private String token(Long userId) {
        return Jwts.builder()
                .claim("userId", userId).claim("email", "rg-test@example.com").claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 2_000))
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
