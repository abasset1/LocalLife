package com.locallife.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.subscription.application.SubscriptionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
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
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Abonnements sans paiement (BILLING-01, ADR-0014) : demande, activation par un administrateur, droits Pro ouverts
 * et retirés au moment prévu, résiliation, refus motivé, changement d'offre, expiration, journal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubscriptionIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ME = "/api/v1/users/me/subscription";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubscriptionService subscriptionService;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void aProRequest_ShouldOpenTheProSpace_OnlyOnceApproved_AndCancellationKeepsItUntilTheEnd() throws Exception {
        String user = token(user("USER"), "USER");
        String admin = token(user("ADMIN"), "ADMIN");
        call(HttpMethod.GET, ME, null, null, 401);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("FREE");
        call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "GOLD"), 400);

        JsonNode requested = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "pro"), 200);
        assertThat(requested.get("plan").asText()).isEqualTo("FREE");
        assertThat(requested.get("pending").get("plan").asText()).isEqualTo("PRO");
        call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PREMIUM"), 409);
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);

        long id = requested.get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", user, Map.of(), 403);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/subscriptions", admin, null, 200))
                .extracting(item -> item.get("subscription").get("id").asLong()).contains(id);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", admin, Map.of("endsOn", "2020-01-01"),
                400);
        String endsOn = LocalDate.now().plusDays(30).toString();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", admin, Map.of("endsOn", endsOn), 200);

        JsonNode active = call(HttpMethod.GET, ME, user, null, 200);
        assertThat(active.get("plan").asText()).isEqualTo("PRO");
        assertThat(active.get("current").get("accessEndsAt").asText()).isNotBlank();
        // Le droit Pro vient de l'abonnement, sans rôle PRO ni nouvelle connexion.
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 200);
        call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PRO"), 409);

        JsonNode canceled = call(HttpMethod.POST, ME + "/cancel", user, null, 200);
        assertThat(canceled.get("current").get("status").asText()).isEqualTo("CANCELED");
        assertThat(canceled.get("plan").asText()).isEqualTo("PRO");
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 200);
        call(HttpMethod.POST, ME + "/cancel", user, null, 404);

        // Échéance passée : droits retirés tout de suite, puis expiration par la tâche planifiée.
        jdbcTemplate.update("UPDATE subscription SET current_period_end = now() - interval '1 minute' WHERE id = ?",
                id);
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);
        subscriptionService.expireDue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM subscription WHERE id = ?", String.class, id))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForList("SELECT to_status FROM subscription_event WHERE subscription_id = ? "
                + "ORDER BY id", String.class, id)).containsExactly("REQUESTED", "ACTIVE", "CANCELED", "EXPIRED");
    }

    @Test
    void withoutAnEndDate_CancellationShouldEndAccessAtOnce_AndAChangeOfPlanReplacesTheCurrentOne() throws Exception {
        String user = token(user("USER"), "USER");
        String admin = token(user("ADMIN"), "ADMIN");

        long premium = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PREMIUM"), 200)
                .get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + premium + "/approve", admin, null, 200);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("PREMIUM");
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);

        long pro = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PRO"), 200)
                .get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + pro + "/approve", admin, null, 200);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM subscription WHERE id = ?", String.class, premium))
                .isEqualTo("REPLACED");
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("PRO");

        JsonNode canceled = call(HttpMethod.POST, ME + "/cancel", user, null, 200);
        assertThat(canceled.get("plan").asText()).isEqualTo("FREE");
        assertThat(canceled.get("current").isNull()).isTrue();
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);
    }

    @Test
    void aRequest_CanBeWithdrawnOrRejectedWithAReason_AndAnAccessRevoked() throws Exception {
        String user = token(user("USER"), "USER");
        String admin = token(user("ADMIN"), "ADMIN");

        call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PRO"), 200);
        assertThat(call(HttpMethod.POST, ME + "/withdraw", user, null, 200).get("pending").isNull()).isTrue();
        call(HttpMethod.POST, ME + "/withdraw", user, null, 404);

        long rejected = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PRO"), 200)
                .get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + rejected + "/reject", admin, Map.of(), 400);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + rejected + "/reject", admin,
                Map.of("reason", "Activité non professionnelle"), 200);
        JsonNode afterRejection = call(HttpMethod.GET, ME, user, null, 200);
        assertThat(afterRejection.get("history").get(0).get("decisionReason").asText())
                .isEqualTo("Activité non professionnelle");

        long granted = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PRO"), 200)
                .get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + granted + "/approve", admin, null, 200);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + granted + "/revoke", admin,
                Map.of("reason", "Abus"), 200);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("FREE");
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + granted + "/revoke", admin, null, 404);
    }

    // ---------------------------------------------------------------- outils

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, role, created_at, email_verified) "
                + "VALUES (?, ?, ?, now(), true) RETURNING id", Long.class, "sub-" + unique,
                "sub-" + unique + "@example.com", role);
        userIds.add(id);
        return id;
    }

    private String token(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "sub-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
