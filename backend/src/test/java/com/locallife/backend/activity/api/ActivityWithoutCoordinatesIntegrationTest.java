package com.locallife.backend.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.domain.Role;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Activité sans coordonnées en base ({@code activity.latitude}/{@code longitude} nulles, colonnes nullables depuis
 * V2) : constat hors périmètre de l'audit Search V1 (#93, {@code SEARCH_V1_AUDIT.md}, risque 2). Une telle ligne
 * (données historiques ou insérées à la main) ne doit faire échouer ni sa fiche, ni les listes qui la contiennent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityWithoutCoordinatesIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestAccounts testAccounts;

    private final List<Long> activityIds = new ArrayList<>();
    private String token;
    private long withoutCoordinates;
    private long withCoordinates;

    @BeforeEach
    void setUp() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder("zq");
        for (int i = 0; i < 8; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        token = builder.toString();
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        withoutCoordinates = save("Sans lieu " + token, null, null, start);
        withCoordinates = save("Avec lieu " + token, 45.764, 4.8357, start);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status ->
                activityIds.forEach(id -> jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id)));
    }

    private long save(String title, Double latitude, Double longitude, LocalDateTime start) {
        Long id = transactionTemplate.execute(tx -> {
            Long created = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, category, city, latitude, longitude, status, start_date, "
                            + "source_id) VALUES (?, 'fete', ?, ?, ?, 'PUBLISHED', ?, "
                            + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                    Long.class, title, "Ville" + token, latitude, longitude, start);
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "SELECT ?, id FROM category WHERE code = 'CULTURE'", created);
            return created;
        });
        activityIds.add(id);
        return id;
    }

    private JsonNode get(String uri, String authorization) throws Exception {
        RestTestClient.RequestHeadersSpec<?> request = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build().get().uri(uri);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        String body = request.exchange().expectStatus().isOk().expectBody(String.class).returnResult()
                .getResponseBody();
        return OBJECT_MAPPER.readTree(body);
    }

    private static List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    @Test
    void detail_ShouldBeServed_WithNullCoordinates() throws Exception {
        JsonNode detail = get("/api/v1/activities/" + withoutCoordinates, null);

        assertThat(detail.get("id").asLong()).isEqualTo(withoutCoordinates);
        assertThat(detail.get("latitude").isNull()).isTrue();
        assertThat(detail.get("longitude").isNull()).isTrue();
    }

    @Test
    void publicList_ShouldKeepWorking_AndIncludeTheActivity() throws Exception {
        JsonNode list = get("/api/v1/activities?city=Ville" + token, null);

        assertThat(ids(list)).contains(withoutCoordinates, withCoordinates);
    }

    @Test
    void globalSearch_ShouldKeepWorking_AndFindBothActivities() throws Exception {
        JsonNode result = get("/api/v1/activities/search?q=" + token, null);

        assertThat(ids(result.get("items"))).contains(withoutCoordinates, withCoordinates);
    }

    @Test
    void adminModerationList_ShouldKeepWorking() throws Exception {
        get("/api/v1/admin/activities?status=PUBLISHED", testAccounts.bearer(Role.ADMIN));
    }
}
