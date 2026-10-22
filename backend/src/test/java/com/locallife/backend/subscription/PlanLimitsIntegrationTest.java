package com.locallife.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Limites par offre (décision du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md} § « Limites par offre ») : 5 favoris et
 * 1 alerte pour un compte gratuit, 2 nouvelles contributions par mois civil pour Premium (refusées non comptées),
 * rien de supprimé pour un compte déjà au-delà.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanLimitsIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GeocodingService geocodingService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> activityIds = new ArrayList<>();

    @BeforeEach
    void geocoding() {
        Coordinates lyon = new Coordinates(45.76, 4.83, "Lyon", "69001");
        when(geocodingService.geocode(anyString())).thenReturn(lyon);
        when(geocodingService.resolveCity(any(), any(), any())).thenReturn(lyon);
    }

    @AfterEach
    void cleanUp() {
        Long[] users = userIds.toArray(new Long[0]);
        jdbcTemplate.update("DELETE FROM activity WHERE created_by = ANY (?)", (Object) users);
        activityIds.forEach(id -> jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM subscription WHERE user_id = ANY (?)", (Object) users);
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void aFreeAccount_ShouldKeepFiveFavorites_AndExistingOnesBeyondTheLimit() throws Exception {
        long userId = user();
        String free = token(userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", free, null, 200).get("limits").get("maxLikes").asInt())
                .isEqualTo(5);
        List<Long> activities = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            activities.add(publishedActivity());
        }
        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/api/v1/activities/" + activities.get(i) + "/like", free, null, 200);
        }
        JsonNode refused = call(HttpMethod.POST, "/api/v1/activities/" + activities.get(5) + "/like", free, null, 403);
        assertThat(refused.get("message").asText()).contains("5 favoris");
        // Re-liker un favori existant reste sans effet ; en retirer un libère une place.
        call(HttpMethod.POST, "/api/v1/activities/" + activities.get(0) + "/like", free, null, 200);
        call(HttpMethod.DELETE, "/api/v1/activities/" + activities.get(0) + "/like", free, null, 200);
        call(HttpMethod.POST, "/api/v1/activities/" + activities.get(5) + "/like", free, null, 200);

        // Premium : favoris illimités ; revenu au gratuit, il garde tout mais ne peut plus en ajouter.
        grantPremium(userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", free, null, 200).get("limits").get("maxLikes").isNull())
                .isTrue();
        long seventh = publishedActivity();
        call(HttpMethod.POST, "/api/v1/activities/" + activities.get(0) + "/like", free, null, 200);
        call(HttpMethod.POST, "/api/v1/activities/" + seventh + "/like", free, null, 200);
        jdbcTemplate.update("UPDATE subscription SET status = 'EXPIRED' WHERE user_id = ?", userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me/likes", free, null, 200)).hasSize(7);
        call(HttpMethod.POST, "/api/v1/activities/" + publishedActivity() + "/like", free, null, 403);
    }

    @Test
    void aFreeAccount_ShouldHaveOneAlert_AndAPaidOneTen() throws Exception {
        long userId = user();
        String free = token(userId);
        Map<String, Object> alert = Map.of("categoryCodes", List.of("CULTURE"), "city", "Lyon", "radiusKm", 10);
        call(HttpMethod.POST, "/api/v1/users/me/alerts", free, alert, 201);
        assertThat(call(HttpMethod.POST, "/api/v1/users/me/alerts", free, alert, 403).get("message").asText())
                .contains("1 alerte");

        grantPremium(userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", free, null, 200).get("limits").get("maxAlerts").asInt())
                .isEqualTo(10);
        for (int i = 1; i < 10; i++) {
            call(HttpMethod.POST, "/api/v1/users/me/alerts", free, alert, 201);
        }
        call(HttpMethod.POST, "/api/v1/users/me/alerts", free, alert, 403);
    }

    @Test
    void premium_ShouldContributeTwiceAMonth_RejectedOnesNotCounted() throws Exception {
        long userId = user();
        String premium = token(userId);
        grantPremium(userId);
        long first = call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 201).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 201);
        JsonNode limits = call(HttpMethod.GET, "/api/v1/users/me", premium, null, 200).get("limits");
        assertThat(limits.get("monthlyContributions").asInt()).isEqualTo(2);
        assertThat(limits.get("contributionsThisMonth").asInt()).isEqualTo(2);
        assertThat(call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 403).get("message").asText())
                .contains("2 nouvelles contributions par mois");

        // Une contribution refusée libère sa place ; une contribution du mois précédent ne compte pas.
        jdbcTemplate.update("UPDATE activity SET status = 'REJECTED' WHERE id = ?", first);
        long third = call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 201).get("id").asLong();
        jdbcTemplate.update("UPDATE activity SET created_at = now() - interval '40 days' WHERE id = ?", third);
        call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 201);

        // Les modifications ne comptent pas ; un compte Pro n'a pas de limite.
        call(HttpMethod.PUT, "/api/v1/activities/" + third, premium, contribution(), 200);
        jdbcTemplate.update("UPDATE users SET role = 'PRO', pro_role_source = 'ADMIN' WHERE id = ?", userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", premium, null, 200).get("limits")
                .get("monthlyContributions").isNull()).isTrue();
        call(HttpMethod.POST, "/api/v1/activities", premium, contribution(), 201);
    }

    // ---------------------------------------------------------------- outils

    private static Map<String, Object> contribution() {
        return Map.of("title", "Vide-grenier " + UUID.randomUUID(), "description", "Quartier",
                "address", "Place Bellecour, Lyon", "startDate", "2030-06-01T09:00:00",
                "endDate", "2030-06-01T17:00:00", "categoryCodes", List.of("CULTURE"));
    }

    private long publishedActivity() {
        Long id = jdbcTemplate.queryForObject("""
                WITH a AS (
                    INSERT INTO activity (title, status, latitude, longitude, start_date, source_id)
                    VALUES (?, 'PUBLISHED', 45.76, 4.83, now() + interval '2 days',
                            (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1))
                    RETURNING id
                ), c AS (
                    INSERT INTO activity_category (activity_id, category_id)
                    SELECT a.id, (SELECT id FROM category WHERE code = 'CULTURE') FROM a
                )
                SELECT id FROM a
                """, Long.class, "Concert " + UUID.randomUUID());
        activityIds.add(id);
        return id;
    }

    private void grantPremium(long userId) {
        jdbcTemplate.update("UPDATE subscription SET status = 'EXPIRED' WHERE user_id = ? "
                + "AND status IN ('ACTIVE', 'CANCELED')", userId);
        jdbcTemplate.update("INSERT INTO subscription (user_id, plan, status, provider, current_period_start) "
                + "VALUES (?, 'PREMIUM', 'ACTIVE', 'SIMULATED', now())", userId);
    }

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(OBJECT_MAPPER.writeValueAsString(body));
        }
        String response = spec.exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : OBJECT_MAPPER.readTree(response);
    }

    private long user() {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, role, created_at, email_verified) "
                + "VALUES (?, ?, 'USER', now(), true) RETURNING id", Long.class, "pl-" + unique,
                "pl-" + unique + "@example.com");
        userIds.add(id);
        return id;
    }

    private String token(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "pl-test@example.com").claim("role", "USER")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
