package com.locallife.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.activity.application.AdminActivityService;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.auth.application.JwtService;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.Role;
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
 * Règles des comptes administrateurs (décisions du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md}
 * § « Administrateur ») : journal unique, corbeille de 30 jours, motif et auteur prévenu, jamais sur ses propres
 * contenus, mot de passe redemandé, actions sur les comptes, limite des tentatives de connexion, session de 8 h,
 * routes héritées retirées.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRulesIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PASSWORD = "motDePasseAdmin1";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashingService passwordHashingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminActivityService adminActivityService;

    @MockitoBean
    private EmailService emailService;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        Long[] users = userIds.toArray(new Long[0]);
        jdbcTemplate.update("DELETE FROM activity WHERE created_by = ANY (?)", (Object) users);
        jdbcTemplate.update("DELETE FROM subscription WHERE user_id = ANY (?)", (Object) users);
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void moderation_ShouldRequireAReason_NotifyTheAuthor_AndNeverApplyToOwnContent() throws Exception {
        long authorId = user("USER");
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");
        long pending = activity(authorId, "PENDING");

        call(HttpMethod.PATCH, "/api/v1/admin/activities/" + pending + "/reject", admin, Map.of(), 400);
        call(HttpMethod.PATCH, "/api/v1/admin/activities/" + pending + "/reject", admin,
                Map.of("reason", "Adresse introuvable"), 200);
        verify(emailService).sendProDecisionEmail(anyString(), contains("n'a pas été publiée"),
                contains("Adresse introuvable"));
        assertThat(call(HttpMethod.GET, "/api/v1/activities/" + pending + "/contribution", token(authorId, "USER"),
                null, 200).get("moderationReason").asText()).isEqualTo("Adresse introuvable");

        long own = activity(adminId, "PENDING");
        call(HttpMethod.PATCH, "/api/v1/admin/activities/" + own + "/publish", admin, null, 403);
        call(HttpMethod.PATCH, "/api/v1/admin/activities/" + own + "/publish", token(user("ADMIN"), "ADMIN"), null,
                200);

        JsonNode log = call(HttpMethod.GET, "/api/v1/admin/action-log?targetType=ACTIVITY&targetId=" + pending,
                admin, null, 200);
        assertThat(log.get("items").get(0).get("action").asText()).isEqualTo("ACTIVITY_REJECT");
        assertThat(log.get("items").get(0).get("reason").asText()).isEqualTo("Adresse introuvable");
        assertThat(log.get("items").get(0).get("actorId").asLong()).isEqualTo(adminId);
    }

    @Test
    void deletion_ShouldGoToTheTrash_BeRestorable_AndPurgedAfterThirtyDays() throws Exception {
        long authorId = user("USER");
        String author = token(authorId, "USER");
        String admin = token(user("ADMIN"), "ADMIN");
        long published = activity(authorId, "PUBLISHED");

        call(HttpMethod.DELETE, "/api/v1/admin/activities/" + published, admin, null, 400);
        call(HttpMethod.DELETE, "/api/v1/admin/activities/" + published + "?reason=Doublon", admin, null, 204);
        call(HttpMethod.GET, "/api/v1/activities/" + published, null, null, 404);
        call(HttpMethod.GET, "/api/v1/activities/" + published, author, null, 404);
        assertThat(call(HttpMethod.GET, "/api/v1/activities/mine", author, null, 200)).isEmpty();
        verify(emailService).sendProDecisionEmail(anyString(), contains("a été supprimée"), contains("Doublon"));
        assertThat(call(HttpMethod.GET, "/api/v1/admin/activities/trash?size=200", admin, null, 200).get("items"))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(published));

        call(HttpMethod.POST, "/api/v1/admin/activities/" + published + "/restore", admin, null, 204);
        call(HttpMethod.GET, "/api/v1/activities/" + published, null, null, 200);

        // Purge : seulement après 30 jours de corbeille.
        call(HttpMethod.DELETE, "/api/v1/admin/activities/" + published + "?reason=Doublon", admin, null, 204);
        adminActivityService.purgeExpired();
        assertThat(exists(published)).isTrue();
        jdbcTemplate.update("UPDATE activity SET deleted_at = now() - interval '31 days' WHERE id = ?", published);
        adminActivityService.purgeExpired();
        assertThat(exists(published)).isFalse();
    }

    @Test
    void bulkDeletion_ShouldAskThePassword_AndBeRestorableAsABatch() throws Exception {
        long authorId = user("USER");
        String admin = token(user("ADMIN"), "ADMIN");
        activity(authorId, "REJECTED");
        activity(authorId, "REJECTED");
        long sourceId = jdbcTemplate.queryForObject("SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1",
                Long.class);
        long expected = call(HttpMethod.GET, "/api/v1/admin/activities/deletion-preview?status=REJECTED&sourceId="
                + sourceId, admin, null, 200).get("count").asLong();
        String uri = "/api/v1/admin/activities?status=REJECTED&sourceId=" + sourceId + "&expectedCount=" + expected;

        callWithPassword(HttpMethod.DELETE, uri + "&reason=Nettoyage", admin, null, 428);
        callWithPassword(HttpMethod.DELETE, uri + "&reason=Nettoyage", admin, "mauvais", 428);
        callWithPassword(HttpMethod.DELETE, uri, admin, PASSWORD, 400);
        JsonNode result = callWithPassword(HttpMethod.DELETE, uri + "&reason=Nettoyage", admin, PASSWORD, 200);
        assertThat(result.get("count").asLong()).isEqualTo(expected);
        assertThat(count("SELECT count(*) FROM activity WHERE created_by = ? AND status = 'DELETED'", authorId))
                .isEqualTo(2);

        call(HttpMethod.POST, "/api/v1/admin/activities/trash/batches/" + result.get("batch").asText() + "/restore",
                admin, null, 200);
        assertThat(count("SELECT count(*) FROM activity WHERE created_by = ? AND status = 'REJECTED'", authorId))
                .isEqualTo(2);
    }

    @Test
    void anAdmin_ShouldNotDecideOnHisOwnSubscription() throws Exception {
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");
        long request = call(HttpMethod.POST, "/api/v1/users/me/subscription/subscribe", admin, Map.of("plan", "PRO"),
                200).get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + request + "/approve", admin, Map.of(), 403);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + request + "/approve", token(user("ADMIN"), "ADMIN"),
                Map.of(), 200);
    }

    @Test
    void accounts_ShouldBeLoggedOutEverywhere_OrDeletedWithAReasonAndThePassword() throws Exception {
        long targetId = user("USER");
        String target = token(targetId, "USER", new Date(System.currentTimeMillis() - 5_000));
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");
        call(HttpMethod.GET, "/api/v1/users/me", target, null, 200);

        call(HttpMethod.POST, "/api/v1/admin/accounts/" + targetId + "/logout-everywhere", admin, null, 200);
        call(HttpMethod.GET, "/api/v1/users/me", target, null, 401);
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + adminId + "/logout-everywhere", admin, null, 403);

        String deletion = "/api/v1/admin/accounts/" + targetId + "/deletion";
        callWithPassword(HttpMethod.POST, deletion, admin, null, 428, Map.of("reason", "Spam répété"));
        callWithPassword(HttpMethod.POST, deletion, admin, PASSWORD, 400, Map.of());
        callWithPassword(HttpMethod.POST, deletion, admin, PASSWORD, 204, Map.of("reason", "Spam répété"));
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", targetId)).isZero();
        assertThat(count("SELECT count(*) FROM account_audit_event WHERE target_user_id = ? "
                + "AND action = 'ADMIN_ACCOUNT_DELETION' AND outcome = 'SUCCESS'", targetId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM admin_action_log WHERE target_id = ? "
                + "AND action = 'ACCOUNT_ADMIN_ACCOUNT_DELETION'", String.valueOf(targetId))).isEqualTo(1);
    }

    @Test
    void theActionLog_ShouldRecordEveryAdminWrite_AndRefuseAnyChange() throws Exception {
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        call(HttpMethod.POST, "/api/v1/admin/categories", admin, Map.of("code", code, "label", "Test journal"), 201);
        assertThat(count("SELECT count(*) FROM admin_action_log WHERE actor_id = ? "
                + "AND action = 'POST /api/v1/admin/categories'", adminId)).isEqualTo(1);
        jdbcTemplate.update("DELETE FROM category WHERE code = ?", code);

        try {
            jdbcTemplate.update("DELETE FROM admin_action_log WHERE actor_id = ?", adminId);
            throw new AssertionError("Le journal ne doit pas accepter de suppression");
        } catch (org.springframework.dao.DataAccessException expected) {
            assertThat(expected.getMessage()).contains("ajout seul");
        }
    }

    @Test
    void login_ShouldBeBlockedAfterFiveFailures_AndAdminSessionsLastEightHours() throws Exception {
        long adminId = user("ADMIN");
        String email = jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, adminId);
        Map<String, Object> wrong = Map.of("email", email, "password", "mauvais-mot-de-passe");
        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/api/v1/auth/login", null, wrong, 401);
        }
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD), 429);
        jdbcTemplate.update("DELETE FROM login_failure");

        String issued = call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password",
                PASSWORD), 200).get("token").asText();
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(issued).getPayload();
        long hours = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 3_600_000L;
        assertThat(hours).isEqualTo(8);
        assertThat(jwtService.generateToken(1L, "u@example.com", Role.USER)).isNotBlank();
    }

    @Test
    void legacyUserRoutes_ShouldBeGone() throws Exception {
        String admin = token(user("ADMIN"), "ADMIN");
        assertThat(status(HttpMethod.POST, "/api/v1/users", admin, Map.of("username", "x", "email", "x@x.fr")))
                .isGreaterThanOrEqualTo(400);
        assertThat(status(HttpMethod.GET, "/api/v1/users/1", admin, null)).isGreaterThanOrEqualTo(400);
        assertThat(status(HttpMethod.GET, "/api/v1/admin/users?email=x@x.fr", admin, null))
                .isGreaterThanOrEqualTo(400);
    }

    // ---------------------------------------------------------------- outils

    private long activity(long authorId, String status) {
        // Activité et catégorie dans la même instruction : « au moins une catégorie » est vérifié à la validation.
        return jdbcTemplate.queryForObject("""
                WITH a AS (
                    INSERT INTO activity (title, category, latitude, longitude, status, start_date, source_id,
                                          created_by)
                    VALUES (?, 'fete', 43.3, 5.4, ?, now() + interval '2 days',
                            (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1), ?)
                    RETURNING id
                ), c AS (
                    INSERT INTO activity_category (activity_id, category_id)
                    SELECT a.id, (SELECT id FROM category WHERE code = 'CULTURE') FROM a
                )
                SELECT id FROM a
                """, Long.class, "Activité admin " + UUID.randomUUID(), status, authorId);
    }

    private boolean exists(long activityId) {
        return count("SELECT count(*) FROM activity WHERE id = ?", activityId) == 1;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        return send(method, uri, token, null, body, expectedStatus);
    }

    private JsonNode callWithPassword(HttpMethod method, String uri, String token, String password,
            int expectedStatus) throws Exception {
        return send(method, uri, token, password, null, expectedStatus);
    }

    private JsonNode callWithPassword(HttpMethod method, String uri, String token, String password,
            int expectedStatus, Object body) throws Exception {
        return send(method, uri, token, password, body, expectedStatus);
    }

    private JsonNode send(HttpMethod method, String uri, String token, String password, Object body,
            int expectedStatus) throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (password != null) {
            spec.header("X-Confirm-Password", password);
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(OBJECT_MAPPER.writeValueAsString(body));
        }
        String response = spec.exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : OBJECT_MAPPER.readTree(response);
    }

    private int status(HttpMethod method, String uri, String token, Object body) throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(OBJECT_MAPPER.writeValueAsString(body));
        }
        return spec.exchange().returnResult(String.class).getStatus().value();
    }

    private long user(String role) {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, password_hash, role, created_at, "
                + "email_verified) VALUES (?, ?, ?, ?, now(), true) RETURNING id", Long.class, "ad-" + unique,
                "ad-" + unique + "@example.com", passwordHashingService.hash(PASSWORD), role);
        userIds.add(id);
        return id;
    }

    private String token(Long userId, String role) {
        return token(userId, role, new Date());
    }

    private String token(Long userId, String role, Date issuedAt) {
        return Jwts.builder()
                .claim("userId", userId).claim("email", "ad-test@example.com").claim("role", role)
                .issuedAt(issuedAt).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
