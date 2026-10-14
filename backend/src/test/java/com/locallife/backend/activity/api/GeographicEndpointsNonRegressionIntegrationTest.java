package com.locallife.backend.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Random;
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
 * #93 — La recherche globale (#91, #92) ne change pas les endpoints géographiques : mêmes champs
 * JSON (figés ici tels que livrés avant la recherche globale), mêmes limites de zone et de rayon,
 * activités publiées uniquement. À l'inverse, la recherche globale trouve ce que ces endpoints
 * écartent par la géographie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeographicEndpointsNonRegressionIntegrationTest {

    private static final double[] LYON = {45.7640, 4.8357};
    private static final double[] BORDEAUX = {44.8378, -0.5792};

    /**
     * Élément de {@code /nearby} et {@code /within-bounds} ({@code ActivityResponse}). {@code coordinatePrecision} :
     * ajout non cassant de l'ADR-0011, phase A.
     */
    private static final Set<String> MAP_ITEM_FIELDS = Set.of(
            "id", "title", "description", "category", "categoryCodes", "latitude", "longitude", "startDate",
            "endDate", "status", "url", "address", "city", "postalCode", "sourceName", "createdBy",
            "createdByUsername", "likedByCurrentUser", "mainImageUrl", "publishedAt", "coordinatePrecision");

    /** Élément de {@code GET /api/v1/activities} (résumé) ; {@code coordinatePrecision} : ADR-0011, phase A. */
    private static final Set<String> LIST_ITEM_FIELDS = Set.of(
            "id", "title", "category", "date", "latitude", "longitude", "address", "city", "createdBy",
            "mainImageUrl", "coordinatePrecision");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> activityIds = new ArrayList<>();
    private String token;
    private String city;
    private long lyon;
    private long bordeaux;
    /** Une activité par statut non publié, toutes à Lyon et dans la fenêtre de dates. */
    private final List<Long> unpublished = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Lettres seules : reste un mot après normalisation.
        Random random = new Random();
        StringBuilder builder = new StringBuilder("zr");
        for (int i = 0; i < 8; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        token = builder.toString();
        city = "Villegeo" + token;
        // Dans la fenêtre par défaut de /nearby (7 jours).
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        lyon = save("Kermesse " + token, LYON, "PUBLISHED", start);
        bordeaux = save("Kermesse " + token, BORDEAUX, "PUBLISHED", start);
        for (String status : List.of("PENDING", "REJECTED", "CANCELLED", "ARCHIVED")) {
            unpublished.add(save("Kermesse " + token, LYON, status, start));
        }
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status ->
                activityIds.forEach(id -> jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id)));
    }

    private long save(String title, double[] position, String status, LocalDateTime start) {
        Long id = transactionTemplate.execute(tx -> {
            Long created = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, category, city, latitude, longitude, status, start_date, "
                            + "source_id) VALUES (?, 'fete', ?, ?, ?, ?, ?, "
                            + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                    Long.class, title, city, position[0], position[1], status, start);
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
            return created;
        });
        activityIds.add(id);
        return id;
    }

    private JsonNode get(String uri) throws Exception {
        String body = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri(uri).exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return OBJECT_MAPPER.readTree(body);
    }

    /** Éléments de la réponse créés par ce test (la base peut contenir d'autres activités). */
    private List<JsonNode> ours(JsonNode items) {
        List<JsonNode> result = new ArrayList<>();
        items.forEach(item -> {
            if (activityIds.contains(item.get("id").asLong())) {
                result.add(item);
            }
        });
        return result;
    }

    private static List<Long> ids(List<JsonNode> items) {
        return items.stream().map(item -> item.get("id").asLong()).toList();
    }

    private static Set<String> fields(JsonNode item) {
        Set<String> names = new TreeSet<>();
        item.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void nearby_KeepsItsFieldsAndItsRadius_PublishedOnly() throws Exception {
        List<JsonNode> found = ours(get("/api/v1/activities/nearby?latitude=" + LYON[0] + "&longitude=" + LYON[1]
                + "&radius=10&category=CULTURE"));

        assertThat(ids(found)).containsExactly(lyon);
        assertThat(fields(found.get(0))).isEqualTo(new TreeSet<>(MAP_ITEM_FIELDS));
        assertThat(found.get(0).get("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void withinBounds_KeepsItsFieldsAndItsArea_PublishedOnly() throws Exception {
        List<JsonNode> found = ours(get("/api/v1/activities/within-bounds?swLatitude=45.6&swLongitude=4.7"
                + "&neLatitude=45.9&neLongitude=5.0"));

        assertThat(ids(found)).containsExactly(lyon);
        assertThat(fields(found.get(0))).isEqualTo(new TreeSet<>(MAP_ITEM_FIELDS));
    }

    @Test
    void activitiesList_KeepsItsSummaryFields() throws Exception {
        List<JsonNode> found = ours(get("/api/v1/activities?city=" + city));

        assertThat(ids(found)).containsExactlyInAnyOrder(lyon, bordeaux);
        found.forEach(item -> assertThat(fields(item)).isEqualTo(new TreeSet<>(LIST_ITEM_FIELDS)));
    }

    /**
     * Liste publique : activités publiées uniquement, avec ou sans filtre ni tri. Avant le correctif, elle
     * renvoyait aussi les activités en attente de validation, refusées, annulées et archivées (audit #93).
     */
    @Test
    void activitiesList_NeverReturnsPendingRejectedCancelledOrArchived() throws Exception {
        String day = LocalDateTime.now().plusDays(2).toLocalDate().toString();
        for (String uri : List.of(
                "/api/v1/activities",
                "/api/v1/activities?city=" + city,
                "/api/v1/activities?sort=city,date",
                "/api/v1/activities?date=" + day,
                "/api/v1/activities?city=" + city + "&date=" + day + "&sort=date")) {
            List<Long> found = ids(ours(get(uri)));
            assertThat(found).as(uri).containsExactlyInAnyOrder(lyon, bordeaux);
            assertThat(found).as(uri).doesNotContainAnyElementsOf(unpublished);
        }
    }

    @Test
    void geographicEndpoints_NeverReturnUnpublishedActivities() throws Exception {
        List<Long> nearby = ids(ours(get("/api/v1/activities/nearby?latitude=" + LYON[0] + "&longitude=" + LYON[1]
                + "&radius=10")));
        List<Long> withinBounds = ids(ours(get("/api/v1/activities/within-bounds?swLatitude=45.6&swLongitude=4.7"
                + "&neLatitude=45.9&neLongitude=5.0")));

        assertThat(nearby).containsExactly(lyon);
        assertThat(withinBounds).containsExactly(lyon);
    }

    @Test
    void globalSearch_FindsWhatTheGeographicEndpointsLeaveOut_NeverTheUnpublished() throws Exception {
        JsonNode search = get("/api/v1/activities/search?q=" + token + "&latitude=" + LYON[0]
                + "&longitude=" + LYON[1]);

        assertThat(ids(ours(search.get("items")))).containsExactly(lyon, bordeaux);
        assertThat(search.get("total").asLong()).isEqualTo(2);
        assertThat(ids(ours(search.get("items")))).doesNotContainAnyElementsOf(unpublished);
    }
}
