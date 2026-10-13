package com.locallife.backend.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fenêtre par défaut des 7 prochains jours de {@code /nearby} et {@code /within-bounds} (sans {@code date}) :
 * une occurrence est retenue si elle <b>chevauche</b> la fenêtre (ADR-0011, § 1.3, défaut 3). Cas réel à
 * l'origine : expositions DATAtourisme commencées avant aujourd'hui et finissant après la fenêtre, masquées
 * tant qu'il fallait que leur début ou leur fin tombe dans la fenêtre. Couvre les deux branches : activité
 * programmée (occurrences) et activité sans programmation (colonnes plates).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DefaultDateWindowIntegrationTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    /** Point propre à l'exécution (rayon de 1 km), pour ne pas croiser d'autres activités de la base. */
    private double latitude;
    private double longitude;

    @BeforeEach
    void setUp() {
        Random random = new Random();
        latitude = 46.0 + random.nextDouble();
        longitude = 1.0 + random.nextDouble();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : activityIds) {
                jdbcTemplate.update("DELETE FROM occurrence WHERE schedule_id IN "
                        + "(SELECT id FROM schedule WHERE activity_id = ?)", id);
                jdbcTemplate.update("DELETE FROM schedule WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity_category WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
            }
            locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
        });
    }

    private static LocalDateTime noon(int daysFromToday) {
        return LocalDate.now(PARIS).plusDays(daysFromToday).atTime(LocalTime.NOON);
    }

    private long insertActivity(String title, LocalDateTime start, LocalDateTime end) {
        Long id = transactionTemplate.execute(tx -> {
            Long created = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, category, latitude, longitude, status, start_date, end_date, "
                            + "source_id) VALUES (?, 'expo', ?, ?, 'PUBLISHED', ?, ?, "
                            + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                    Long.class, title, latitude, longitude, start, end);
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
            return created;
        });
        activityIds.add(id);
        return id;
    }

    /** Activité programmée : une occurrence de {@code start} à {@code end}, au point de l'exécution. */
    private long scheduled(String title, int startDay, int endDay) {
        LocalDateTime start = noon(startDay);
        LocalDateTime end = noon(endDay);
        long activityId = insertActivity(title, start, end);
        transactionTemplate.executeWithoutResult(tx -> {
            Long locationId = jdbcTemplate.queryForObject(
                    "INSERT INTO location (name, latitude, longitude, timezone) VALUES (?, ?, ?, 'Europe/Paris') "
                            + "RETURNING id", Long.class, title, latitude, longitude);
            locationIds.add(locationId);
            Long scheduleId = jdbcTemplate.queryForObject(
                    "INSERT INTO schedule (activity_id, location_id) VALUES (?, ?) RETURNING id",
                    Long.class, activityId, locationId);
            jdbcTemplate.update("INSERT INTO occurrence (schedule_id, start_at, end_at, status) "
                    + "VALUES (?, ?, ?, 'SCHEDULED')", scheduleId,
                    Timestamp.from(start.atZone(PARIS).toInstant()), Timestamp.from(end.atZone(PARIS).toInstant()));
        });
        return activityId;
    }

    /** Activité sans programmation : colonnes plates seules. */
    private long unscheduled(String title, int startDay, int endDay) {
        return insertActivity(title, noon(startDay), noon(endDay));
    }

    private Set<Long> found(String uri) throws Exception {
        String body = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri(uri).exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        JsonNode items = OBJECT_MAPPER.readTree(body);
        Set<Long> ids = new HashSet<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        ids.retainAll(activityIds);
        return ids;
    }

    private Set<Long> nearby() throws Exception {
        return found("/api/v1/activities/nearby?latitude=" + latitude + "&longitude=" + longitude + "&radius=1");
    }

    private Set<Long> withinBounds() throws Exception {
        return found("/api/v1/activities/within-bounds?swLatitude=" + (latitude - 0.01)
                + "&swLongitude=" + (longitude - 0.01) + "&neLatitude=" + (latitude + 0.01)
                + "&neLongitude=" + (longitude + 0.01));
    }

    @Test
    void defaultWindow_ShouldShowOccurrencesOverlappingTheNextSevenDays() throws Exception {
        long spanning = scheduled("Exposition commencée et finissant après la fenêtre", -10, 20);
        long inside = scheduled("Concert dans la fenêtre", 2, 2);
        long startingInside = scheduled("Exposition qui commence dans la fenêtre", 5, 40);
        long endingInside = scheduled("Exposition qui finit dans la fenêtre", -30, 3);
        long ended = scheduled("Exposition terminée hier", -10, -1);
        long later = scheduled("Concert après la fenêtre", 9, 9);

        Set<Long> expected = Set.of(spanning, inside, startingInside, endingInside);
        assertThat(nearby()).containsExactlyInAnyOrderElementsOf(expected).doesNotContain(ended, later);
        assertThat(withinBounds()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void defaultWindow_ShouldApplyTheSameRuleToUnscheduledActivities() throws Exception {
        long spanning = unscheduled("Exposition sans programmation, en cours", -10, 20);
        long inside = unscheduled("Fête sans programmation, dans la fenêtre", 1, 1);
        long ended = unscheduled("Fête sans programmation, terminée", -5, -1);
        long later = unscheduled("Fête sans programmation, après la fenêtre", 10, 12);

        Set<Long> expected = Set.of(spanning, inside);
        assertThat(nearby()).containsExactlyInAnyOrderElementsOf(expected).doesNotContain(ended, later);
        assertThat(withinBounds()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
