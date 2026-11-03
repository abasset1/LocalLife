package com.locallife.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.application.EmailService;
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

/** Suggestions et anomalies : envoi par un utilisateur connecté, alerte e-mail des administrateurs, consultation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedbackIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void submit_ShouldSaveTheMessage_AndEmailEveryActiveAdmin() throws Exception {
        long authorId = user("USER");
        String adminEmail = email(user("ADMIN"));
        String suspendedAdminEmail = email(user("ADMIN"));
        jdbcTemplate.update("UPDATE users SET account_status = 'SUSPENDED' WHERE email = ?", suspendedAdminEmail);

        JsonNode created = call(HttpMethod.POST, "/api/v1/feedback", token(authorId, "USER"),
                Map.of("kind", "suggestion", "message", "  Ajouter un filtre « gratuit »  ", "pageUrl", "/?zoom=9"),
                201);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM feedback WHERE id = ?",
                created.get("id").asLong());
        assertThat(row.get("kind")).isEqualTo("SUGGESTION");
        assertThat(row.get("message")).isEqualTo("Ajouter un filtre « gratuit »");
        assertThat(row.get("page_url")).isEqualTo("/?zoom=9");
        assertThat(row.get("status")).isEqualTo("NEW");
        verify(emailService).sendAdminNotificationEmail(eq(adminEmail), contains("Nouvelle suggestion"),
                contains("Ajouter un filtre « gratuit »"));
        verify(emailService, never()).sendAdminNotificationEmail(eq(suspendedAdminEmail), anyString(), anyString());
    }

    @Test
    void submit_ShouldRequireALogin_AndAValidMessage() throws Exception {
        String author = token(user("USER"), "USER");
        call(HttpMethod.POST, "/api/v1/feedback", null, Map.of("kind", "BUG", "message", "La carte est vide"), 401);
        call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "BUG", "message", "  "), 400);
        call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "AUTRE", "message", "La carte est vide"), 400);
        call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "BUG", "message", "x".repeat(2001)), 400);
    }

    @Test
    void submit_ShouldBeLimitedToTenMessagesPerHour() throws Exception {
        String author = token(user("USER"), "USER");
        for (int i = 0; i < 10; i++) {
            call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "BUG", "message", "Problème n°" + i), 201);
        }
        call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "BUG", "message", "Encore un problème"), 400);
    }

    @Test
    void admin_ShouldListMessages_AndMarkThemDone_OtherRolesCannot() throws Exception {
        long authorId = user("USER");
        String author = token(authorId, "USER");
        long id = call(HttpMethod.POST, "/api/v1/feedback", author,
                Map.of("kind", "BUG", "message", "Le bouton Suivre ne répond pas"), 201).get("id").asLong();
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");

        call(HttpMethod.GET, "/api/v1/admin/feedback", author, null, 403);
        call(HttpMethod.GET, "/api/v1/admin/feedback", token(user("LOCALLIFE"), "LOCALLIFE"), null, 403);

        JsonNode page = call(HttpMethod.GET, "/api/v1/admin/feedback?status=NEW&size=100", admin, null, 200);
        assertThat(page.get("newCount").asLong()).isPositive();
        JsonNode item = find(page, id);
        assertThat(item.get("kind").asText()).isEqualTo("BUG");
        assertThat(item.get("username").asText()).startsWith("fb-");
        assertThat(item.get("email").asText()).endsWith("@example.com");

        JsonNode done = call(HttpMethod.PATCH, "/api/v1/admin/feedback/" + id, admin, Map.of("done", true), 200);
        assertThat(done.get("status").asText()).isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForObject("SELECT handled_by FROM feedback WHERE id = ?", Long.class, id))
                .isEqualTo(adminId);
        assertThat(find(call(HttpMethod.GET, "/api/v1/admin/feedback?status=NEW&size=100", admin, null, 200), id))
                .isNull();

        call(HttpMethod.PATCH, "/api/v1/admin/feedback/" + id, admin, Map.of("done", false), 200);
        call(HttpMethod.PATCH, "/api/v1/admin/feedback/999999999", admin, Map.of("done", true), 404);
        call(HttpMethod.GET, "/api/v1/admin/feedback?status=AUTRE", admin, null, 400);
    }

    @Test
    void feedback_ShouldBePartOfTheDataExport_AndDeletedWithTheAccount() throws Exception {
        long authorId = user("USER");
        String author = token(authorId, "USER");
        call(HttpMethod.POST, "/api/v1/feedback", author, Map.of("kind", "SUGGESTION", "message", "Un mode sombre"),
                201);

        JsonNode export = call(HttpMethod.GET, "/api/v1/users/me/export", author, null, 200);
        assertThat(export.get("feedback").get(0).get("message").asText()).isEqualTo("Un mode sombre");

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM feedback WHERE user_id = ?", Long.class,
                authorId)).isZero();
    }

    private static JsonNode find(JsonNode page, long id) {
        for (JsonNode item : page.get("items")) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        return null;
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

    private long user(String role) {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, password_hash, role, created_at, "
                + "email_verified) VALUES (?, ?, 'x', ?, now(), true) RETURNING id", Long.class, "fb-" + unique,
                "fb-" + unique + "@example.com", role);
        userIds.add(id);
        return id;
    }

    private String email(long userId) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private String token(Long userId, String role) {
        return Jwts.builder()
                .claim("userId", userId).claim("email", "fb-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
