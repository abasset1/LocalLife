package com.locallife.backend.place.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rapport des doublons de lieux d'événement (ADR-0011, § 7.2.13) : grappes, exclusions, et AUCUNE écriture. Code
 * postal fictif (99903) pour isoler les données du test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventLocationDuplicateIntegrationTest {

    private static final String POSTAL = "99903";
    private static final double METER = 0.000009;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : activityIds) {
                jdbcTemplate.update("DELETE FROM schedule WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity_category WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
            }
            locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
        });
    }

    private String admin() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .claim("userId", 1L).claim("email", "dup-test@example.com").claim("role", "ADMIN")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long location(String address, double metersNorth, String precision, boolean used) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO location (address, postal_code, city, latitude, longitude, timezone, "
                        + "coordinate_precision) VALUES (?, ?, 'Carpentras', ?, 5.05, 'Europe/Paris', ?) RETURNING id",
                Long.class, address, POSTAL, 44.05 + metersNorth * METER, precision);
        locationIds.add(id);
        if (used) {
            Long activity = transactionTemplate.execute(status -> {
                Long created = jdbcTemplate.queryForObject(
                        "INSERT INTO activity (title, latitude, longitude, status, source_id) VALUES ('Visite', "
                                + "44.05, 5.05, 'PUBLISHED', (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) "
                                + "RETURNING id", Long.class);
                jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                        + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
                return created;
            });
            activityIds.add(activity);
            jdbcTemplate.update("INSERT INTO schedule (activity_id, location_id) VALUES (?, ?)", activity, id);
        }
        return id;
    }

    private String snapshot() {
        return jdbcTemplate.queryForObject("SELECT string_agg(id || ':' || address || ':' || latitude, ',' "
                + "ORDER BY id) FROM location WHERE postal_code = ?", String.class, POSTAL);
    }

    @Test
    void report_ShouldGroupDuplicatesIntoClusters_WithoutWriting() {
        Long first = location("8 Place Aristide Briand Château de Simiane", 0, "UNKNOWN", true);
        Long second = location("8 place Aristide Briand Château de Simiane", 1, "UNKNOWN", true);
        Long third = location("Château de Simiane", 40, "UNKNOWN", true);
        Long otherNumber = location("5 place Aristide Briand Château de Simiane", 2, "UNKNOWN", true);
        location("8 place Aristide Briand Château de Simiane", 3, "APPROXIMATE", true);
        location("8 place Aristide Briand Château de Simiane", 4, "UNKNOWN", false);
        String before = snapshot();

        client().get().uri("/api/v1/admin/places/event-location-duplicates?department=" + POSTAL)
                .header(HttpHeaders.AUTHORIZATION, admin()).exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clusters.length()").isEqualTo(3)
                .jsonPath("$.clusters[0].level").isEqualTo("CERTAIN")
                .jsonPath("$.clusters[0].locations[*].id").isEqualTo(List.of(first.intValue(), second.intValue()))
                .jsonPath("$.clusters[0].activityCount").isEqualTo(2)
                .jsonPath("$.extraRowsByLevel.CERTAIN").isEqualTo(1)
                // Le texte court est rapproché de la grappe et de l'autre numéro, sans les relier entre eux.
                .jsonPath("$.clusters[1].level").isEqualTo("PROBABLE")
                .jsonPath("$.clusters[1].locations[*].id").isEqualTo(List.of(first.intValue(), third.intValue()))
                .jsonPath("$.clusters[2].locations[*].id").isEqualTo(List.of(third.intValue(),
                        otherNumber.intValue()));

        assertThat(snapshot()).as("le rapport n'écrit rien").isEqualTo(before);
    }

    @Test
    void report_ShouldBeReservedToAdmins_AndValidateParameters() {
        client().get().uri("/api/v1/admin/places/event-location-duplicates").exchange().expectStatus()
                .isUnauthorized();
        client().get().uri("/api/v1/admin/places/event-location-duplicates?level=MERGE")
                .header(HttpHeaders.AUTHORIZATION, admin()).exchange().expectStatus().isBadRequest();
        client().get().uri("/api/v1/admin/places/event-location-duplicates?limit=0")
                .header(HttpHeaders.AUTHORIZATION, admin()).exchange().expectStatus().isBadRequest();
    }
}
