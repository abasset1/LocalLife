package com.locallife.backend.place.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fiche d'un lieu permanent (ADR-0011, § 7.2.10) : lieu publié seulement ; activités à venir du lieu et des lieux
 * d'événement reliés, jamais celles d'un lieu non relié, passées ou non publiées. Public.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceDetailIntegrationTest {

    @LocalServerPort
    private int port;

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

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Long location(String name, String placeStatus) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO location (name, postal_code, city, latitude, longitude, timezone, place_status, place_type) "
                        + "VALUES (?, '99902', 'Avignon', 43.95, 4.80, 'Europe/Paris', ?, ?) RETURNING id",
                Long.class, name, placeStatus, placeStatus == null ? null : "Theater");
        locationIds.add(id);
        return id;
    }

    private Long activityAt(Long locationId, String title, String status, int startInDays) {
        Long id = transactionTemplate.execute(tx -> {
            Long created = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, latitude, longitude, status, start_date, end_date, source_id) "
                            + "VALUES (?, 43.95, 4.80, ?, ?, ?, (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) "
                            + "RETURNING id",
                    Long.class, title, status, LocalDateTime.now().plusDays(startInDays),
                    LocalDateTime.now().plusDays(startInDays).plusHours(2));
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
            return created;
        });
        activityIds.add(id);
        jdbcTemplate.update("INSERT INTO schedule (activity_id, location_id) VALUES (?, ?)", id, locationId);
        return id;
    }

    @Test
    void placeCard_ShouldListUpcomingActivitiesOfThePlaceAndOfLinkedEventLocations() {
        Long theatre = location("Théâtre du Chêne Noir", "PUBLISHED");
        Long linked = location(null, null);
        Long notLinked = location("Salle voisine", null);
        jdbcTemplate.update("UPDATE location SET place_id = ? WHERE id = ?", theatre, linked);
        Long later = activityAt(linked, "Concert dans 10 jours", "PUBLISHED", 10);
        Long sooner = activityAt(theatre, "Pièce dans 2 jours", "PUBLISHED", 2);
        activityAt(linked, "Spectacle passé", "PUBLISHED", -10);
        activityAt(linked, "En attente de modération", "PENDING", 3);
        activityAt(notLinked, "Ailleurs", "PUBLISHED", 1);

        client().get().uri("/api/v1/places/" + theatre).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Théâtre du Chêne Noir");

        List<Map<String, Object>> activities = client().get().uri("/api/v1/activities/at-place/" + theatre)
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() { })
                .returnResult().getResponseBody();
        assertThat(activities).extracting(item -> ((Number) item.get("id")).longValue())
                .containsExactly(sooner, later);
        assertThat(activities.get(0)).containsKeys("title", "startDate", "sourceName", "coordinatePrecision");
    }

    @Test
    void placeCard_ShouldBeNotFound_ForAnUnpublishedPlace() {
        Long pending = location("Musée en attente", "PENDING");
        Long eventLocation = location("Salle", null);
        client().get().uri("/api/v1/places/" + pending).exchange().expectStatus().isNotFound();
        client().get().uri("/api/v1/activities/at-place/" + pending).exchange().expectStatus().isNotFound();
        client().get().uri("/api/v1/places/" + eventLocation).exchange().expectStatus().isNotFound();
        client().get().uri("/api/v1/activities/at-place/999999999").exchange().expectStatus().isNotFound();
    }
}
