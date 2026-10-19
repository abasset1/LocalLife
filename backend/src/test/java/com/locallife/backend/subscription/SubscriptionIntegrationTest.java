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
 * Abonnements sans paiement (BILLING-01, ADR-0014) : souscription activée tout de suite (paiement simulé), droits Pro
 * ouverts et retirés au moment prévu, résiliation, changement d'offre, expiration, journal ; anciennes demandes encore
 * traitées par un administrateur.
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
    void subscribingToPro_ShouldOpenTheProSpaceAtOnce_AndCancellationWithoutPeriodEndsItAtOnce() throws Exception {
        String user = token(user("USER"), "USER");
        call(HttpMethod.GET, ME, null, null, 401);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("FREE");
        call(HttpMethod.POST, ME + "/subscribe", user, Map.of("plan", "GOLD"), 400);
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);

        // Paiement réputé validé : activation immédiate, sans échéance ni administrateur.
        JsonNode subscribed = call(HttpMethod.POST, ME + "/subscribe", user, Map.of("plan", "pro"), 200);
        assertThat(subscribed.get("plan").asText()).isEqualTo("PRO");
        assertThat(subscribed.get("pending").isNull()).isTrue();
        assertThat(subscribed.get("current").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(subscribed.get("current").get("accessEndsAt").isNull()).isTrue();
        long id = subscribed.get("current").get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT provider FROM subscription WHERE id = ?", String.class, id))
                .isEqualTo("SIMULATED");
        // Le droit Pro vient de l'abonnement, sans rôle PRO ni nouvelle connexion.
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 200);
        call(HttpMethod.POST, ME + "/subscribe", user, Map.of("plan", "PRO"), 409);

        JsonNode canceled = call(HttpMethod.POST, ME + "/cancel", user, null, 200);
        assertThat(canceled.get("plan").asText()).isEqualTo("FREE");
        assertThat(canceled.get("current").isNull()).isTrue();
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);
        call(HttpMethod.POST, ME + "/cancel", user, null, 404);
        assertThat(jdbcTemplate.queryForList("SELECT to_status FROM subscription_event WHERE subscription_id = ? "
                + "ORDER BY id", String.class, id)).containsExactly("REQUESTED", "ACTIVE", "EXPIRED");
    }

    @Test
    void aChangeOfPlan_ShouldReplaceTheCurrentOne_AndTheOldRequestPathStillSubscribes() throws Exception {
        String user = token(user("USER"), "USER");

        long premium = call(HttpMethod.POST, ME + "/request", user, Map.of("plan", "PREMIUM"), 200)
                .get("current").get("id").asLong();
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("plan").asText()).isEqualTo("PREMIUM");
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 403);

        assertThat(call(HttpMethod.POST, ME + "/subscribe", user, Map.of("plan", "PRO"), 200).get("plan").asText())
                .isEqualTo("PRO");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM subscription WHERE id = ?", String.class, premium))
                .isEqualTo("REPLACED");
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 200);
    }

    @Test
    void aLegacyRequest_CanStillBeApprovedWithAnEndDate_AndCancellationKeepsAccessUntilTheEnd() throws Exception {
        long userId = user("USER");
        String user = token(userId, "USER");
        String admin = token(user("ADMIN"), "ADMIN");
        long id = legacyRequest(userId, "PRO");

        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", user, Map.of(), 403);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/subscriptions", admin, null, 200))
                .extracting(item -> item.get("subscription").get("id").asLong()).contains(id);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", admin, Map.of("endsOn", "2020-01-01"),
                400);
        String endsOn = LocalDate.now().plusDays(30).toString();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", admin, Map.of("endsOn", endsOn), 200);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("current").get("accessEndsAt").asText())
                .isNotBlank();

        JsonNode canceled = call(HttpMethod.POST, ME + "/cancel", user, null, 200);
        assertThat(canceled.get("current").get("status").asText()).isEqualTo("CANCELED");
        assertThat(canceled.get("plan").asText()).isEqualTo("PRO");
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 200);

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
    void legacyRequests_CanBeWithdrawnRejectedOrSuperseded_AndAnAccessRevoked() throws Exception {
        long userId = user("USER");
        String user = token(userId, "USER");
        String admin = token(user("ADMIN"), "ADMIN");

        legacyRequest(userId, "PRO");
        assertThat(call(HttpMethod.POST, ME + "/withdraw", user, null, 200).get("pending").isNull()).isTrue();
        call(HttpMethod.POST, ME + "/withdraw", user, null, 404);

        long rejected = legacyRequest(userId, "PRO");
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + rejected + "/reject", admin, Map.of(), 400);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + rejected + "/reject", admin,
                Map.of("reason", "Activité non professionnelle"), 200);
        assertThat(call(HttpMethod.GET, ME, user, null, 200).get("history").get(0).get("decisionReason").asText())
                .isEqualTo("Activité non professionnelle");

        // Une souscription retire la demande encore en attente.
        long superseded = legacyRequest(userId, "PREMIUM");
        JsonNode subscribed = call(HttpMethod.POST, ME + "/subscribe", user, Map.of("plan", "PRO"), 200);
        assertThat(subscribed.get("pending").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM subscription WHERE id = ?", String.class,
                superseded)).isEqualTo("WITHDRAWN");

        long granted = subscribed.get("current").get("id").asLong();
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

    /** Demande en attente d'un administrateur, comme avant la souscription directe. */
    private long legacyRequest(long userId, String plan) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO subscription (user_id, plan, status) "
                + "VALUES (?, ?, 'REQUESTED') RETURNING id", Long.class, userId, plan);
        jdbcTemplate.update("INSERT INTO subscription_event (subscription_id, actor_id, to_status) "
                + "VALUES (?, ?, 'REQUESTED')", id, userId);
        return id;
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
