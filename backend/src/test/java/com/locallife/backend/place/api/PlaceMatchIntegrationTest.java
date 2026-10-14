package com.locallife.backend.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.locallife.backend.place.api.PlaceMatchResponse.Item;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
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
 * Rapport de rapprochement lieux d'événement ↔ lieux permanents (ADR-0011, § 2.3) : classement, exclusions,
 * et AUCUNE écriture. Code postal fictif (99901) pour isoler les données du test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceMatchIntegrationTest {

    private static final String POSTAL = "99901";
    private static final double LAT = 43.9493;
    private static final double LON = 4.8055;
    /** Environ 1 m de latitude. */
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
        activityIds.forEach(id -> {
            jdbcTemplate.update("DELETE FROM schedule WHERE activity_id = ?", id);
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM activity_category WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
            });
        });
        locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
    }

    private String admin() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .claim("userId", 1L).claim("email", "match-test@example.com").claim("role", "ADMIN")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long location(String name, String address, double metersNorth, String precision, String placeStatus) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO location (name, address, postal_code, city, latitude, longitude, timezone, "
                        + "coordinate_precision, place_status, place_type) "
                        + "VALUES (?, ?, ?, 'Avignon', ?, ?, 'Europe/Paris', ?, ?, ?) RETURNING id",
                Long.class, name, address, POSTAL, LAT + metersNorth * METER, LON, precision, placeStatus,
                placeStatus == null ? null : "Theater");
        locationIds.add(id);
        return id;
    }

    private Long eventLocation(String name, String address, double metersNorth, String precision) {
        Long id = location(name, address, metersNorth, precision, null);
        Long activity = transactionTemplate.execute(status -> {
            Long created = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, latitude, longitude, status, source_id) VALUES (?, ?, ?, "
                            + "'PUBLISHED', (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                    Long.class, "Spectacle à " + name, LAT, LON);
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
            return created;
        });
        activityIds.add(activity);
        jdbcTemplate.update("INSERT INTO schedule (activity_id, location_id) VALUES (?, ?)", activity, id);
        return id;
    }

    private String snapshot() {
        return jdbcTemplate.queryForObject(
                "SELECT string_agg(id || ':' || name || ':' || coalesce(place_status, '-') || ':' || latitude, ',' "
                        + "ORDER BY id) FROM location WHERE postal_code = ?", String.class, POSTAL);
    }

    @Test
    void report_ShouldClassifyPairs_WithoutWriting() {
        Long theatre = location("Théâtre du Chêne Noir", "8 bis rue Sainte-Catherine", 0, "EXACT", "PUBLISHED");
        Long certain = eventLocation("Theatre du Chene Noir", "8 bis rue Sainte Catherine", 10, "UNKNOWN");
        Long probable = eventLocation("Chêne Noir", null, 60, "UNKNOWN");
        Long sameAddress = eventLocation("Salle Léo Ferré", "8 bis rue Sainte-Catherine", 20, "UNKNOWN");
        eventLocation("Théâtre du Chêne Noir", null, 5, "APPROXIMATE");
        location("Théâtre du Chêne Noir", null, 15, "UNKNOWN", null);
        Long blancs = location("Chapelle des Pénitents Blancs", null, 3000, "EXACT", "PENDING");
        Long noirs = location("Chapelle des Pénitents Noirs", null, 3060, "EXACT", "PENDING");
        Long twoCandidates = eventLocation("Chapelle des Pénitents", null, 3030, "UNKNOWN");
        String before = snapshot();

        var body = client().get().uri("/api/v1/admin/places/matches?department=" + POSTAL + "&limit=200")
                .header(HttpHeaders.AUTHORIZATION, admin()).exchange().expectStatus().isOk()
                .expectBody(PlaceMatchResponse.class).returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.matches()).extracting(Item::eventLocationId, Item::placeId, Item::level)
                .containsExactlyInAnyOrder(
                        tuple(certain, theatre, "CERTAIN"),
                        tuple(probable, theatre, "PROBABLE"),
                        tuple(sameAddress, theatre, "AMBIGUOUS"),
                        tuple(twoCandidates, blancs, "AMBIGUOUS"),
                        tuple(twoCandidates, noirs, "AMBIGUOUS"));
        assertThat(body.matches()).filteredOn(item -> item.eventLocationId().equals(twoCandidates))
                .allSatisfy(item -> assertThat(item.reason()).isEqualTo("plusieurs candidats"));
        assertThat(body.eventLocationsByLevel()).containsEntry(
                Level.AMBIGUOUS, 2);
        assertThat(body.activitiesByLevel()).containsEntry(
                Level.CERTAIN, 1);
        assertThat(snapshot()).as("le rapport n'écrit rien").isEqualTo(before);
    }

    @Test
    void report_ShouldBeReservedToAdmins_AndValidateParameters() {
        client().get().uri("/api/v1/admin/places/matches").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/v1/admin/places/matches?level=MERGE").header(HttpHeaders.AUTHORIZATION, admin())
                .exchange().expectStatus().isBadRequest();
        client().get().uri("/api/v1/admin/places/matches?limit=0").header(HttpHeaders.AUTHORIZATION, admin())
                .exchange().expectStatus().isBadRequest();
        client().get().uri("/api/v1/admin/places/matches?department=" + POSTAL + "&level=certain")
                .header(HttpHeaders.AUTHORIZATION, admin()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.matches").isArray();
    }
}
