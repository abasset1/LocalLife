package com.locallife.backend.foodtruck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Food trucks de l'espace professionnel (PRO-01 lot C) et leurs présences (FOODTRUCK-01) : compte professionnel
 * exigé, propriété vérifiée côté serveur, brouillon puis première publication validée par l'équipe (22/10/2026),
 * chevauchements refusés, emplacement sur un lieu existant ou saisi, suspension et archivage, suspension par un
 * administrateur, affichage public gratuit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProFoodTruckIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        List<Long> spots = jdbcTemplate.queryForList("SELECT p.location_id FROM food_truck_presence p "
                + "JOIN food_truck ft ON ft.id = p.food_truck_id WHERE ft.owner_id = ANY (?)", Long.class,
                (Object) userIds.toArray(new Long[0]));
        jdbcTemplate.update("DELETE FROM food_truck WHERE owner_id = ANY (?)", (Object) userIds.toArray(new Long[0]));
        spots.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ? AND place_type IS NULL", id));
        locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void profile_ShouldRequireAProAccount_StartAsDraft_AndBePrivateToItsOwner() throws Exception {
        String owner = token(user("PRO"), "PRO");
        String other = token(user("PRO"), "PRO");
        call(HttpMethod.GET, "/api/v1/pro/foodtrucks", null, null, 401);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks", token(user("USER"), "USER"), profile("Refusé"), 403);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks", owner, Map.of("name", "Sans cuisine"), 400);

        long truck = createTruck(owner, "Tacos Loco");
        assertThat(status(call(HttpMethod.GET, "/api/v1/pro/foodtrucks/" + truck, owner, null, 200)))
                .isEqualTo("DRAFT");
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 404);
        call(HttpMethod.GET, "/api/v1/pro/foodtrucks/" + truck, other, null, 404);
        call(HttpMethod.PUT, "/api/v1/pro/foodtrucks/" + truck, other, profile("Pirate"), 404);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", other, null, 404);
        assertThat(call(HttpMethod.GET, "/api/v1/pro/foodtrucks", other, null, 200)).isEmpty();

        // Première publication : validée par l'équipe (22/10/2026), invisible en attendant.
        assertThat(status(call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200)))
                .isEqualTo("PENDING");
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 404);
        String admin = token(user("ADMIN"), "ADMIN");
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/approve", owner, null, 403);
        JsonNode approved = call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/approve", admin, null, 200);
        assertThat(status(approved)).isEqualTo("PUBLISHED");
        assertThat(approved.get("approved").asBoolean()).isTrue();
        assertThat(call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 200).get("name").asText())
                .startsWith("Tacos Loco");
        assertThat(call(HttpMethod.PUT, "/api/v1/pro/foodtrucks/" + truck, owner, profile("Tacos Loco 2"), 200)
                .get("name").asText()).startsWith("Tacos Loco 2");

        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/suspend", owner, null, 200);
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 404);
        // Une fois validé, il se republie sans nouvelle validation.
        assertThat(status(call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200)))
                .isEqualTo("PUBLISHED");
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/archive", owner, null, 200);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 409);
        call(HttpMethod.PUT, "/api/v1/pro/foodtrucks/" + truck, owner, profile("Trop tard"), 409);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/teleport", owner, null, 404);
    }

    @Test
    void presences_ShouldBePreviewedAsDrafts_ThenPublished_AndShownOnlyWhenActive() throws Exception {
        String owner = token(user("PRO"), "PRO");
        long truck = createTruck(owner, "Crêpes du Rhône");
        publishValidated(truck, owner);
        double latitude = 44 + ThreadLocalRandom.current().nextDouble(0.5);
        double longitude = 4 + ThreadLocalRandom.current().nextDouble(0.5);

        JsonNode draft = call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner,
                presence(newSpot(latitude, longitude), "PRIVATE", List.of(), "11:30", "14:00"), 201);
        long presence = draft.get("id").asLong();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("spotName").asText()).isEqualTo("Parking de la zone");
        assertThat(ours(truck)).isEmpty();

        call(HttpMethod.POST, "/api/v1/pro/presences/" + presence + "/publish", owner, null, 200);
        JsonNode shown = ours(truck);
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).get("latitude").asDouble()).isEqualTo(latitude);
        assertThat(shown.get(0).get("startTime").asText()).startsWith("11:30");

        // Un food truck n'est pas à deux endroits au même moment ; bout à bout, c'est possible.
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner,
                presence(newSpot(latitude + 0.1, longitude), "EVENT", List.of("SA"), "13:00", "15:00"), 409);
        long evening = call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner,
                presence(newSpot(latitude + 0.1, longitude), "EVENT", List.of(), "18:00", "22:00"), 201)
                .get("id").asLong();
        call(HttpMethod.PUT, "/api/v1/pro/presences/" + evening, owner,
                presence(null, "EVENT", List.of(), "12:00", "19:00"), 409);
        call(HttpMethod.POST, "/api/v1/pro/presences/" + evening + "/publish", owner, null, 200);
        assertThat(ours(truck)).hasSize(2);

        // Désactiver puis réactiver ; archiver.
        call(HttpMethod.POST, "/api/v1/pro/presences/" + presence + "/suspend", owner, null, 200);
        assertThat(ours(truck)).hasSize(1);
        call(HttpMethod.POST, "/api/v1/pro/presences/" + presence + "/publish", owner, null, 200);
        assertThat(ours(truck)).hasSize(2);
        call(HttpMethod.POST, "/api/v1/pro/presences/" + evening + "/archive", owner, null, 200);
        call(HttpMethod.PUT, "/api/v1/pro/presences/" + evening, owner,
                presence(null, "EVENT", List.of(), "18:00", "22:00"), 409);
        assertThat(ours(truck)).hasSize(1);
        assertThat(call(HttpMethod.GET, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner, null, 200))
                .hasSize(2);

        // Validations.
        String path = "/api/v1/pro/foodtrucks/" + truck + "/presences";
        call(HttpMethod.POST, path, owner, presence(newSpot(latitude, longitude), "MARKET", List.of("LUN"), null,
                null), 400);
        call(HttpMethod.POST, path, owner, presence(newSpot(latitude, longitude), "PARKING", List.of(), null, null),
                400);
        call(HttpMethod.POST, path, owner, presence(newSpot(latitude, longitude), "MARKET", List.of("SU"), "14:00",
                "09:00"), 400);
        call(HttpMethod.POST, path, owner, with(presence(newSpot(latitude, longitude), "MARKET", List.of("SU"), null,
                null), "validUntil", "2020-01-01"), 400);
        call(HttpMethod.POST, path, owner, presence(null, "MARKET", List.of("SU"), null, null), 400);
    }

    @Test
    void aPresenceAtAnExistingPlace_ShouldLinkToIt_AndAnotherProCannotTouchIt() throws Exception {
        String owner = token(user("PRO"), "PRO");
        String other = token(user("PRO"), "PRO");
        long truck = createTruck(owner, "Burgers Sud");
        publishValidated(truck, owner);
        long market = place("PUBLISHED");
        long pending = place("PENDING");

        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner,
                presence(Map.of("placeId", pending), "MARKET", List.of(), null, null), 400);
        long presence = call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", owner,
                presence(Map.of("placeId", market), "MARKET", List.of(), "07:00", "13:00"), 201).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/pro/presences/" + presence + "/publish", other, null, 404);
        call(HttpMethod.GET, "/api/v1/pro/foodtrucks/" + truck + "/presences", other, null, 404);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/presences", other,
                presence(Map.of("placeId", market), "MARKET", List.of(), null, null), 404);
        call(HttpMethod.POST, "/api/v1/pro/presences/" + presence + "/publish", owner, null, 200);

        assertThat(ours(truck).get(0).get("placeId").asLong()).isEqualTo(market);
        assertThat(call(HttpMethod.GET, "/api/v1/places/" + market + "/foodtrucks", null, null, 200))
                .extracting(node -> node.get("id").asLong()).contains(truck);
        // Le lieu n'est pas modifié et le food truck n'en devient pas un.
        assertThat(jdbcTemplate.queryForObject("SELECT place_status FROM location WHERE id = ?", String.class,
                market)).isEqualTo("PUBLISHED");
    }

    @Test
    void anAdminSuspension_ShouldHideTheTruck_AndOnlyAnAdminCanLiftIt() throws Exception {
        long ownerId = user("PRO");
        String owner = token(ownerId, "PRO");
        String admin = token(user("ADMIN"), "ADMIN");
        long truck = createTruck(owner, "Pizza Nomade");
        publishValidated(truck, owner);

        call(HttpMethod.PATCH, "/api/v1/admin/foodtrucks/" + truck + "/suspend", owner, null, 403);
        JsonNode suspended = call(HttpMethod.PATCH, "/api/v1/admin/foodtrucks/" + truck + "/suspend", admin, null,
                200);
        assertThat(suspended.get("suspendedByLocalLife").asBoolean()).isTrue();
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 404);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 409);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/foodtrucks?status=SUSPENDED&limit=200", admin, null, 200))
                .anySatisfy(item -> assertThat(item.get("ownerId").asLong()).isEqualTo(ownerId));

        call(HttpMethod.PATCH, "/api/v1/admin/foodtrucks/" + truck + "/reinstate", admin, null, 200);
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 200);
        call(HttpMethod.PATCH, "/api/v1/admin/foodtrucks/" + truck + "/reinstate", admin, null, 409);
    }

    @Test
    void aRejectedFirstPublication_CanBeFixedAndResent_AndAnAdminNeverValidatesHisOwnTruck() throws Exception {
        String owner = token(user("PRO"), "PRO");
        String admin = token(user("ADMIN"), "ADMIN");
        long truck = createTruck(owner, "Gaufres");
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/approve", admin, null, 409);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200);
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/reject", admin, Map.of(), 400);
        JsonNode rejected = call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/reject", admin,
                Map.of("reason", "Photo de menu illisible"), 200);
        assertThat(status(rejected)).isEqualTo("REJECTED");
        assertThat(rejected.get("reviewReason").asText()).isEqualTo("Photo de menu illisible");
        call(HttpMethod.PUT, "/api/v1/pro/foodtrucks/" + truck, owner, profile("Gaufres corrigées"), 200);
        assertThat(status(call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200)))
                .isEqualTo("PENDING");
        // Retirer la demande : retour en brouillon.
        assertThat(status(call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/suspend", owner, null, 200)))
                .isEqualTo("DRAFT");

        // L'espace pro d'un administrateur reste ouvert, mais un autre administrateur valide ses food trucks.
        long own = createTruck(admin, "Camion de l'admin");
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + own + "/publish", admin, null, 200);
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + own + "/approve", admin, null, 403);
    }

    // ---------------------------------------------------------------- outils

    /** Première publication puis validation par un administrateur. */
    private void publishValidated(long truck, String owner) throws Exception {
        assertThat(status(call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200)))
                .isEqualTo("PENDING");
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/approve", token(user("ADMIN"), "ADMIN"),
                null, 200);
    }

    private long createTruck(String owner, String name) throws Exception {
        return call(HttpMethod.POST, "/api/v1/pro/foodtrucks", owner, profile(name), 201).get("id").asLong();
    }

    private static Map<String, Object> profile(String name) {
        return Map.of("name", name + " " + UUID.randomUUID(), "category", "Street food", "description", "Fait maison",
                "contact", "06 00 00 00 00");
    }

    private static Map<String, Object> newSpot(double latitude, double longitude) {
        return Map.of("newSpot", Map.of("name", "Parking de la zone", "address", "1 rue Test", "postalCode", "84000",
                "city", "Testville", "latitude", latitude, "longitude", longitude));
    }

    private static Map<String, Object> presence(Map<String, Object> spot, String kind, List<String> days,
            String start, String end) {
        Map<String, Object> body = new HashMap<>(spot == null ? Map.of() : spot);
        body.put("spotKind", kind);
        body.put("days", days);
        body.put("startTime", start);
        body.put("endTime", end);
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    /** Présences publiques du jour pour ce food truck. */
    private JsonNode ours(long truck) throws Exception {
        JsonNode all = call(HttpMethod.GET, "/api/v1/foodtrucks?date=" + LocalDate.now(ZoneId.of("Europe/Paris")),
                null, null, 200);
        var result = OBJECT_MAPPER.createArrayNode();
        all.forEach(item -> {
            if (item.get("id").asLong() == truck) {
                result.add(item);
            }
        });
        return result;
    }

    private static String status(JsonNode node) {
        return node.get("status").asText();
    }

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
                + "VALUES (?, ?, ?, now(), true) RETURNING id", Long.class, "ft-" + unique,
                "ft-" + unique + "@example.com", role);
        userIds.add(id);
        return id;
    }

    private long place(String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO location (name, latitude, longitude, timezone, place_type, "
                + "place_status) VALUES (?, ?, ?, 'Europe/Paris', 'CoveredMarket', ?) RETURNING id", Long.class,
                "Halle " + UUID.randomUUID(), 43 + ThreadLocalRandom.current().nextDouble(),
                5 + ThreadLocalRandom.current().nextDouble(), status);
        locationIds.add(id);
        return id;
    }

    private String token(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "ft-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
