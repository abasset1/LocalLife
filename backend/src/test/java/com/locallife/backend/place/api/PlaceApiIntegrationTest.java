package com.locallife.backend.place.api;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * API des lieux permanents (ADR-0011, phase B) : accès réservé aux administrateurs pour la modération, lecture
 * publique des lieux publiés, validations. Aucun appel réseau : la seule source de lieux (V62) est inactive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceApiIntegrationTest {

    /** Comptes réels : le rôle est relu en base par JwtFilter (voir TestAccounts). */
    @Autowired
    private TestAccounts testAccounts;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", testAccounts.sharedAccount(Role.valueOf(role))).claim("email", "places-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    @Test
    void moderationQueue_ShouldBeReservedToAdmins() {
        client().get().uri("/api/v1/admin/places").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/v1/admin/places")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                .exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/admin/places?status=PENDING&department=84&limit=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items").isArray().jsonPath("$.limit").isEqualTo(10);
    }

    @Test
    void moderation_ShouldValidateRequests() {
        String admin = "Bearer " + token("ADMIN");
        client().get().uri("/api/v1/admin/places?status=UNKNOWN").header(HttpHeaders.AUTHORIZATION, admin)
                .exchange().expectStatus().isBadRequest();
        client().get().uri("/api/v1/admin/places?limit=1000").header(HttpHeaders.AUTHORIZATION, admin)
                .exchange().expectStatus().isBadRequest();
        client().patch().uri("/api/v1/admin/places/999999999/publish").header(HttpHeaders.AUTHORIZATION, admin)
                .exchange().expectStatus().isNotFound();
        client().post().uri("/api/v1/admin/places/moderation").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).body("{\"ids\":[1],\"action\":\"DELETE\"}")
                .exchange().expectStatus().isBadRequest();
        client().post().uri("/api/v1/admin/places/moderation").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).body("{\"ids\":[],\"action\":\"PUBLISH\"}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void import_ShouldRequireAnActiveSource_AndAPlaceSource() {
        String admin = "Bearer " + token("ADMIN");
        Long placeSource = jdbcTemplate.queryForObject(
                "SELECT id FROM source WHERE type = 'DATATOURISME_LIEUX' AND status = 'INACTIVE' ORDER BY id LIMIT 1",
                Long.class);
        client().post().uri("/api/v1/admin/places/sources/" + placeSource + "/import")
                .header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isEqualTo(409);
        Long eventSource = jdbcTemplate.queryForObject(
                "SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1", Long.class);
        client().post().uri("/api/v1/admin/places/sources/" + eventSource + "/import")
                .header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isNotFound();
        client().post().uri("/api/v1/admin/places/sources/" + eventSource + "/dry-run")
                .header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isNotFound();
    }

    @Test
    void publishedPlaces_ShouldBePublic_AndValidateBounds() {
        client().get().uri("/api/v1/places/within-bounds?minLat=43&maxLat=45&minLng=4&maxLng=6")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$").isArray();
        client().get().uri("/api/v1/places/within-bounds?minLat=45&maxLat=43&minLng=4&maxLng=6")
                .exchange().expectStatus().isBadRequest();
    }
}
