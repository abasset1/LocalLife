package com.locallife.backend.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Lancement progressif (05/11/2026) : lieux, food trucks, Premium et Pro fermés au public, ouverts à l'équipe ; les
 * activités, les catégories, les comptes gratuits et l'administration restent ouverts. Propriétés propres : contexte
 * Spring à part, fermé après la classe ({@code DirtiesContext}) pour rendre ses connexions PostgreSQL aux autres tests.
 */
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "locallife.features.places=false",
    "locallife.features.food-trucks=false",
    "locallife.features.premium=false",
    "locallife.features.pro=false",
})
class LaunchFeaturesIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BOUNDS = "/api/v1/places/within-bounds?minLat=43&maxLat=44&minLng=4&maxLng=5";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void closedFeatures_AreHiddenFromVisitorsAndFreeAccounts_ButNotFromTheTeam() throws Exception {
        String user = token(user("USER"), "USER");
        String admin = token(user("ADMIN"), "ADMIN");

        JsonNode visitorFeatures = call(HttpMethod.GET, "/api/v1/features", null, null, 200);
        assertThat(visitorFeatures.get("places").asBoolean()).isFalse();
        assertThat(visitorFeatures.get("foodTrucks").asBoolean()).isFalse();
        assertThat(visitorFeatures.get("premium").asBoolean()).isFalse();
        assertThat(visitorFeatures.get("pro").asBoolean()).isFalse();
        assertThat(call(HttpMethod.GET, "/api/v1/features", user, null, 200).get("places").asBoolean()).isFalse();
        JsonNode adminFeatures = call(HttpMethod.GET, "/api/v1/features", admin, null, 200);
        assertThat(adminFeatures.get("places").asBoolean()).isTrue();
        assertThat(adminFeatures.get("foodTrucks").asBoolean()).isTrue();
        assertThat(adminFeatures.get("premium").asBoolean()).isTrue();
        assertThat(adminFeatures.get("pro").asBoolean()).isTrue();

        // Lieux et food trucks : 404 pour le public, ouverts à l'équipe.
        assertThat(call(HttpMethod.GET, BOUNDS, null, null, 404).get("message").asText())
                .isEqualTo("Cette fonctionnalité n'est pas encore disponible.");
        call(HttpMethod.GET, BOUNDS, user, null, 404);
        call(HttpMethod.GET, BOUNDS, admin, null, 200);
        call(HttpMethod.GET, "/api/v1/activities/at-place/1", null, null, 404);
        call(HttpMethod.GET, "/api/v1/foodtrucks", null, null, 404);
        call(HttpMethod.GET, "/api/v1/foodtrucks", admin, null, 200);

        // Espace pro : connexion toujours exigée d'abord, puis fermé.
        call(HttpMethod.GET, "/api/v1/pro/places", null, null, 401);
        call(HttpMethod.GET, "/api/v1/pro/places", user, null, 404);
        call(HttpMethod.GET, "/api/v1/pro/foodtrucks", user, null, 404);
        call(HttpMethod.GET, "/api/v1/pro/places", admin, null, 200);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", admin, null, 200).get("hasProAccess").asBoolean())
                .isTrue();

        // Premium et Pro : pas de souscription ni de suivi tant que l'offre est fermée.
        call(HttpMethod.POST, "/api/v1/users/me/subscription/subscribe", user, Map.of("plan", "PREMIUM"), 403);
        call(HttpMethod.POST, "/api/v1/users/me/subscription/subscribe", user, Map.of("plan", "PRO"), 403);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me/subscription", user, null, 200).get("plan").asText())
                .isEqualTo("FREE");
        call(HttpMethod.GET, "/api/v1/users/me/follows", user, null, 404);
        call(HttpMethod.POST, "/api/v1/activities/1/follow", user, null, 404);
    }

    @Test
    void activitiesFreeAccountsAndAdministration_StayOpen() throws Exception {
        String user = token(user("USER"), "USER");
        String admin = token(user("ADMIN"), "ADMIN");

        call(HttpMethod.GET, "/api/v1/activities", null, null, 200);
        call(HttpMethod.GET, "/api/v1/categories", null, null, 200);
        JsonNode me = call(HttpMethod.GET, "/api/v1/users/me", user, null, 200);
        assertThat(me.get("hasProAccess").asBoolean()).isFalse();
        call(HttpMethod.GET, "/api/v1/users/me/alerts", user, null, 200);
        call(HttpMethod.GET, "/api/v1/admin/places", admin, null, 200);
        call(HttpMethod.GET, "/api/v1/admin/foodtrucks", admin, null, 200);
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
                + "VALUES (?, ?, ?, now(), true) RETURNING id", Long.class, "launch-" + unique,
                "launch-" + unique + "@example.com", role);
        userIds.add(id);
        return id;
    }

    private String token(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "launch-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
