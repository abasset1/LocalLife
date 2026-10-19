package com.locallife.backend.foodtruck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Food trucks, emplacements et présences (ADR-0013, ARCHI-01) : un food truck n'est pas un lieu, sa position vient de
 * ses présences ; une présence n'est montrée que le jour où elle est valable ; un emplacement peut être un lieu
 * LocalLife existant et accueillir plusieurs food trucks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoodTruckPresenceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Un samedi. */
    private static final String SATURDAY = "2026-10-24";
    private static final String SUNDAY = "2026-10-25";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> truckIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        truckIds.forEach(id -> jdbcTemplate.update("DELETE FROM food_truck WHERE id = ?", id));
        // Les lieux d'événement reliés d'abord : ils pointent vers les lieux permanents.
        locationIds.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
    }

    @Test
    void presences_ShouldBeShownOnlyOnTheDaysTheyAreValid() throws Exception {
        Long truck = truck("PUBLISHED");
        Long market = spot(null, null);
        Long saturdays = presence(truck, market, "MARKET", "SA", "08:00", "13:00", null, null, "PUBLISHED");
        Long spot = spot(null, null);
        Long expired = presence(truck, spot, "OTHER", null, null, null, "2026-01-01", "2026-10-01", "PUBLISHED");
        Long future = presence(truck, spot, "EVENT", null, null, null, "2026-11-01", null, "PUBLISHED");
        Long suspended = presence(truck, spot, "OTHER", null, null, null, null, null, "SUSPENDED");

        JsonNode saturday = ours(get("/api/v1/foodtrucks?date=" + SATURDAY), truck);
        assertThat(saturday).extracting(item -> item.get("presenceId").asLong()).containsExactly(saturdays);
        JsonNode item = saturday.get(0);
        assertThat(item.get("id").asLong()).isEqualTo(truck);
        assertThat(item.get("spotKind").asText()).isEqualTo("MARKET");
        assertThat(item.get("startTime").asText()).startsWith("08:00");
        assertThat(item.get("latitude").asDouble()).isEqualTo(latitudeOf(market));

        assertThat(ours(get("/api/v1/foodtrucks?date=" + SUNDAY), truck)).isEmpty();
        assertThat(ours(get("/api/v1/foodtrucks?date=2026-11-02"), truck))
                .extracting(node -> node.get("presenceId").asLong()).containsExactly(future);
        assertThat(ours(get("/api/v1/foodtrucks?date=2026-09-15"), truck))
                .extracting(node -> node.get("presenceId").asLong()).containsExactly(expired);
        assertThat(suspended).isNotNull();
        client().get().uri("/api/v1/foodtrucks?date=24/10/2026").exchange().expectStatus().isBadRequest();
    }

    @Test
    void aTruckAtTwoSpots_ShouldAppearTwice_AndASpotShouldHostSeveralTrucks() throws Exception {
        Long first = truck("PUBLISHED");
        Long second = truck("PUBLISHED");
        Long hidden = truck("PENDING");
        Long shared = spot(null, null);
        presence(first, shared, "PRIVATE", null, "11:00", "14:00", null, null, "PUBLISHED");
        presence(first, spot(null, null), "PRIVATE", null, "18:00", "22:00", null, null, "PUBLISHED");
        presence(second, shared, "PRIVATE", null, "18:00", "22:00", null, null, "PUBLISHED");
        presence(hidden, shared, "PRIVATE", null, null, null, null, null, "PUBLISHED");

        JsonNode all = get("/api/v1/foodtrucks?date=" + SATURDAY);
        assertThat(ours(all, first)).hasSize(2);
        assertThat(ours(all, second)).hasSize(1);
        assertThat(ours(all, hidden)).isEmpty();
    }

    @Test
    void aSpotThatIsALocalLifePlace_ShouldLinkToIt_AndListItsTrucks() throws Exception {
        Long truck = truck("PUBLISHED");
        Long place = spot("CoveredMarket", "PUBLISHED");
        // Emplacement d'événement relié au lieu permanent (ADR-0011, § 7.2.9).
        Long linkedSpot = spot(null, null);
        jdbcTemplate.update("UPDATE location SET place_id = ? WHERE id = ?", place, linkedSpot);
        Long other = truck("PUBLISHED");
        presence(truck, place, "MARKET", null, null, null, null, null, "PUBLISHED");
        presence(other, linkedSpot, "EVENT", null, null, null, null, null, "PUBLISHED");

        assertThat(ours(get("/api/v1/foodtrucks?date=" + SATURDAY), truck).get(0).get("placeId").asLong())
                .isEqualTo(place);
        assertThat(ours(get("/api/v1/foodtrucks?date=" + SATURDAY), other).get(0).get("placeId").asLong())
                .isEqualTo(place);
        JsonNode atPlace = get("/api/v1/places/" + place + "/foodtrucks");
        assertThat(atPlace).extracting(node -> node.get("id").asLong()).containsExactlyInAnyOrder(truck, other);
        // Le food truck n'est pas devenu un lieu : la couche « Lieux » ne le connaît pas.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM location WHERE place_type IS NOT NULL "
                + "AND id IN (SELECT location_id FROM food_truck_presence WHERE food_truck_id = ?) AND id <> ?",
                Long.class, truck, place)).isZero();
    }

    @Test
    void detail_ShouldSeparateTheProfileFromItsSchedule() throws Exception {
        Long truck = truck("PUBLISHED");
        Long spot = spot(null, null);
        Long weekly = presence(truck, spot, "MARKET", "TU,SA", "08:00", "13:00", null, null, "PUBLISHED");
        presence(truck, spot, "OTHER", null, null, null, "2020-01-01", "2020-12-31", "PUBLISHED");
        presence(truck, spot, "OTHER", null, null, null, null, null, "SUSPENDED");

        JsonNode detail = get("/api/v1/foodtrucks/" + truck);
        assertThat(detail.get("category").asText()).isEqualTo("Tacos");
        assertThat(detail.has("latitude")).isFalse();
        assertThat(detail.get("presences")).extracting(node -> node.get("id").asLong()).containsExactly(weekly);
        assertThat(detail.get("presences").get(0).get("daysOfWeek").asText()).isEqualTo("TU,SA");

        client().get().uri("/api/v1/foodtrucks/" + truck("PENDING")).exchange().expectStatus().isNotFound();
    }

    // ---------------------------------------------------------------- outils

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private JsonNode get(String uri) throws Exception {
        return OBJECT_MAPPER.readTree(client().get().uri(uri).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody());
    }

    /** Éléments de la réponse qui concernent ce food truck (la base peut en contenir d'autres). */
    private static JsonNode ours(JsonNode items, Long truck) {
        var result = OBJECT_MAPPER.createArrayNode();
        items.forEach(item -> {
            if (item.get("id").asLong() == truck) {
                result.add(item);
            }
        });
        return result;
    }

    private Long truck(String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO food_truck (name, category, status) "
                + "VALUES (?, 'Tacos', ?) RETURNING id", Long.class, "Camion " + UUID.randomUUID(), status);
        truckIds.add(id);
        return id;
    }

    private Long spot(String placeType, String placeStatus) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO location (name, latitude, longitude, timezone, place_type, "
                + "place_status) VALUES (?, ?, ?, 'Europe/Paris', ?, ?) RETURNING id", Long.class,
                "Emplacement " + UUID.randomUUID(), 43 + ThreadLocalRandom.current().nextDouble(),
                5 + ThreadLocalRandom.current().nextDouble(), placeType, placeStatus);
        locationIds.add(id);
        return id;
    }

    private Long presence(Long truck, Long spot, String kind, String days, String start, String end,
            String validFrom, String validUntil, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, days_of_week, start_time,
                                                 end_time, valid_from, valid_until, status)
                VALUES (?, ?, ?, ?, CAST(? AS time), CAST(? AS time), CAST(? AS date), CAST(? AS date), ?)
                RETURNING id
                """, Long.class, truck, spot, kind, days, start, end, validFrom, validUntil, status);
    }

    private double latitudeOf(Long location) {
        return jdbcTemplate.queryForObject("SELECT latitude FROM location WHERE id = ?", Double.class, location);
    }
}
