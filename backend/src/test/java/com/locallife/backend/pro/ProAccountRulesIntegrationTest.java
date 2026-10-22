package com.locallife.backend.pro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Règles des comptes professionnels (décisions du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md} § « Professionnel ») :
 * l'offre Pro pilote le rôle, la fin de l'accès masque et révoque puis tout revient, les lieux publiés gérés se
 * modifient par une proposition validée, un gestionnaire renonce ou se voit retirer un lieu, un administrateur ne
 * valide jamais ses propres contenus, une activité peut être proposée par un lieu géré.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProAccountRulesIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ME = "/api/v1/users/me";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private GeocodingService geocodingService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    @BeforeEach
    void geocoding() {
        when(geocodingService.geocode(anyString())).thenReturn(new Coordinates(43.95, 4.81, "Avignon", "84000"));
    }

    @AfterEach
    void cleanUp() {
        Long[] users = userIds.toArray(new Long[0]);
        jdbcTemplate.update("DELETE FROM activity WHERE created_by = ANY (?)", (Object) users);
        jdbcTemplate.update("DELETE FROM food_truck WHERE owner_id = ANY (?)", (Object) users);
        jdbcTemplate.update("DELETE FROM subscription WHERE user_id = ANY (?)", (Object) users);
        jdbcTemplate.update("DELETE FROM place_claim WHERE requester_id = ANY (?)", (Object) users);
        locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM location WHERE created_by = ANY (?)", (Object) users);
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void theProOffer_ShouldDriveTheRole_AndItsEnd_ShouldHideThenRestoreTheProContent() throws Exception {
        long userId = user("USER");
        String pro = token(userId, "USER");
        String admin = token(user("ADMIN"), "ADMIN");
        assertThat(call(HttpMethod.GET, ME, pro, null, 200).get("hasProAccess").asBoolean()).isFalse();

        // Demande puis validation : le compte devient PRO, prévenu par e-mail.
        long first = approvePro(userId, pro, admin);
        assertThat(roleState(userId)).isEqualTo("PRO/SUBSCRIPTION");
        assertThat(call(HttpMethod.GET, ME, pro, null, 200).get("hasProAccess").asBoolean()).isTrue();
        verify(emailService, atLeastOnce()).sendProDecisionEmail(anyString(), contains("Pro"), anyString());

        // Un rôle issu de l'offre ne se retire pas à la main ; Premium ne remplace pas une offre Pro en cours.
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + userId + "/role", admin, Map.of("role", "USER"), 409);
        call(HttpMethod.POST, ME + "/subscription/subscribe", pro, Map.of("plan", "PREMIUM"), 409);

        // Contenus professionnels : un food truck publié, un lieu géré, une demande de gestion en cours.
        long truck = publishedTruck(pro, admin);
        long managed = place("PUBLISHED");
        grantManager(managed, userId);
        long claimed = place("PUBLISHED");
        long claim = call(HttpMethod.POST, "/api/v1/pro/places/" + claimed + "/claims", pro,
                Map.of("relationship", "Gérant"), 201).get("id").asLong();

        // Fin de l'accès (retrait par l'équipe) : rôle retiré, food truck masqué, droits révoqués, demande retirée.
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + first + "/revoke", admin, Map.of("reason", "Test"),
                200);
        assertThat(roleState(userId)).isEqualTo("USER/-");
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 404);
        assertThat(managerActive(managed, userId)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM place_claim WHERE id = ?", String.class, claim))
                .isEqualTo("WITHDRAWN");
        // L'ex-professionnel consulte et archive encore ses food trucks, sans rien pouvoir publier.
        JsonNode mine = call(HttpMethod.GET, "/api/v1/pro/foodtrucks", pro, null, 200);
        assertThat(mine.get(0).get("suspendedForAccess").asBoolean()).isTrue();
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", pro, null, 403);
        call(HttpMethod.GET, "/api/v1/pro/places", pro, null, 403);

        // Retour de l'accès : food truck republié, droit de gestion rétabli (la demande retirée ne revient pas).
        approvePro(userId, pro, admin);
        call(HttpMethod.GET, "/api/v1/foodtrucks/" + truck, null, null, 200);
        assertThat(managerActive(managed, userId)).isTrue();

        // Archivage possible même sans accès.
        long current = currentSubscription(userId);
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + current + "/revoke", admin, Map.of(), 200);
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/archive", pro, null, 200);
    }

    @Test
    void aRoleGrantedByAnAdmin_ShouldNotDependOnTheOffer_AndItsRemoval_ShouldEndTheAccess() throws Exception {
        long userId = user("USER");
        String admin = token(user("ADMIN"), "ADMIN");
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + userId + "/role", admin, Map.of("role", "PRO"), 200);
        assertThat(roleState(userId)).isEqualTo("PRO/ADMIN");
        long managed = place("PUBLISHED");
        grantManager(managed, userId);

        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + userId + "/role", admin, Map.of("role", "USER"), 200);
        assertThat(roleState(userId)).isEqualTo("USER/-");
        assertThat(managerActive(managed, userId)).isFalse();
    }

    @Test
    void cancellingAProOfferWithoutEndDate_ShouldKeepItUntilTheEndOfTheMonth() throws Exception {
        long userId = user("USER");
        String pro = token(userId, "USER");
        approvePro(userId, pro, token(user("ADMIN"), "ADMIN"));

        JsonNode overview = call(HttpMethod.POST, ME + "/subscription/cancel", pro, null, 200);
        assertThat(overview.get("plan").asText()).isEqualTo("PRO");
        assertThat(overview.get("current").get("status").asText()).isEqualTo("CANCELED");
        OffsetDateTime end = OffsetDateTime.parse(overview.get("current").get("accessEndsAt").asText());
        assertThat(end.atZoneSameInstant(ZoneId.of("Europe/Paris")).getDayOfMonth()).isEqualTo(1);
        assertThat(end).isAfter(OffsetDateTime.now());
        assertThat(roleState(userId)).isEqualTo("PRO/SUBSCRIPTION");
    }

    @Test
    void aManagerOfAPublishedPlace_ShouldProposeChanges_AppliedOnlyAfterValidation() throws Exception {
        long managerId = user("PRO");
        String manager = token(managerId, "PRO");
        String admin = token(user("ADMIN"), "ADMIN");
        long placeId = place("PUBLISHED");
        grantManager(placeId, managerId);
        assertThat(call(HttpMethod.GET, "/api/v1/pro/places/" + placeId, manager, null, 200)
                .get("changeProposable").asBoolean()).isTrue();

        Map<String, Object> proposal = new HashMap<>(Map.of("address", "1 rue de la République",
                "postalCode", "84000", "city", "Avignon", "description", "Nouvelle description",
                "website", "https://halle.example.com", "phone", "04 90 00 00 00"));
        long change = call(HttpMethod.POST, "/api/v1/pro/places/" + placeId + "/changes", manager, proposal, 201)
                .get("id").asLong();
        // Une nouvelle proposition remplace celle en attente.
        proposal.put("description", "Description corrigée");
        assertThat(call(HttpMethod.POST, "/api/v1/pro/places/" + placeId + "/changes", manager, proposal, 201)
                .get("id").asLong()).isEqualTo(change);
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM location WHERE id = ?", String.class,
                placeId)).isNull();

        call(HttpMethod.POST, "/api/v1/pro/places/" + placeId + "/changes", token(user("PRO"), "PRO"), proposal, 404);
        call(HttpMethod.POST, "/api/v1/admin/place-changes/" + change + "/reject", admin, Map.of(), 400);
        JsonNode review = call(HttpMethod.GET, "/api/v1/admin/place-changes/" + change, admin, null, 200);
        assertThat(review.get("current").get("description").isNull()).isTrue();
        assertThat(review.get("proposal").get("change").get("description").asText()).isEqualTo("Description corrigée");

        call(HttpMethod.POST, "/api/v1/admin/place-changes/" + change + "/approve", admin, null, 200);
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM location WHERE id = ?", String.class,
                placeId)).isEqualTo("Description corrigée");
        assertThat(jdbcTemplate.queryForObject("SELECT manager_edited_at IS NOT NULL FROM location WHERE id = ?",
                Boolean.class, placeId)).isTrue();
        verify(emailService).sendProDecisionEmail(anyString(), contains("publiée"), anyString());
        call(HttpMethod.POST, "/api/v1/admin/place-changes/" + change + "/approve", admin, null, 409);
    }

    @Test
    void anAdmin_ShouldNeverValidateHisOwnProposalOrPlace() throws Exception {
        long adminId = user("ADMIN");
        String admin = token(adminId, "ADMIN");
        long placeId = place("PUBLISHED");
        grantManager(placeId, adminId);
        long change = call(HttpMethod.POST, "/api/v1/pro/places/" + placeId + "/changes", admin,
                Map.of("city", "Avignon", "address", "2 place de l'Horloge"), 201).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/place-changes/" + change + "/approve", admin, null, 403);

        long created = call(HttpMethod.POST, "/api/v1/pro/places", admin, Map.of("name", "Lieu de l'admin "
                + UUID.randomUUID(), "placeType", "Museum", "city", "Avignon", "latitude", 10.0 + Math.random(),
                "longitude", 10.0 + Math.random(), "confirmNotDuplicate", true), 201).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/pro/places/" + created + "/submit", admin, null, 200);
        call(HttpMethod.PATCH, "/api/v1/admin/places/" + created + "/publish", admin, null, 403);
        call(HttpMethod.PATCH, "/api/v1/admin/places/" + created + "/publish", token(user("ADMIN"), "ADMIN"), null,
                200);
    }

    @Test
    void aManager_ShouldRenounce_AndAnAdmin_ShouldRevokeWithAReason() throws Exception {
        long firstId = user("PRO");
        long secondId = user("PRO");
        String first = token(firstId, "PRO");
        String admin = token(user("ADMIN"), "ADMIN");
        long placeId = place("PUBLISHED");
        grantManager(placeId, firstId);
        grantManager(placeId, secondId);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/places/" + placeId + "/managers", admin, null, 200))
                .hasSize(2);

        call(HttpMethod.POST, "/api/v1/pro/places/" + placeId + "/renounce", first, null, 204);
        assertThat(managerActive(placeId, firstId)).isFalse();
        call(HttpMethod.GET, "/api/v1/pro/places/" + placeId, first, null, 404);

        call(HttpMethod.POST, "/api/v1/admin/places/" + placeId + "/managers/" + secondId + "/revoke", admin,
                Map.of(), 400);
        call(HttpMethod.POST, "/api/v1/admin/places/" + placeId + "/managers/" + secondId + "/revoke", admin,
                Map.of("reason", "N'exploite plus le lieu"), 204);
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_reason || ':' || revoke_comment FROM place_manager "
                + "WHERE location_id = ? AND user_id = ?", String.class, placeId, secondId))
                .isEqualTo("ADMIN:N'exploite plus le lieu");
        call(HttpMethod.POST, "/api/v1/admin/places/" + placeId + "/managers/" + secondId + "/revoke", admin,
                Map.of("reason", "Encore"), 404);
    }

    @Test
    void anActivity_ShouldBeProposedByAPlaceItsAuthorManages_WhileHeManagesIt() throws Exception {
        long proId = user("PRO");
        String pro = token(proId, "PRO");
        long placeId = place("PUBLISHED");
        long notManaged = place("PUBLISHED");
        grantManager(placeId, proId);

        call(HttpMethod.POST, "/api/v1/activities", pro, activity(notManaged), 400);
        long activityId = call(HttpMethod.POST, "/api/v1/activities", pro, activity(placeId), 201).get("id")
                .asLong();
        assertThat(call(HttpMethod.GET, "/api/v1/activities/" + activityId + "/contribution", pro, null, 200)
                .get("hostPlaceId").asLong()).isEqualTo(placeId);
        JsonNode detail = call(HttpMethod.GET, "/api/v1/activities/" + activityId, pro, null, 200);
        assertThat(detail.get("hostPlace").get("id").asLong()).isEqualTo(placeId);

        jdbcTemplate.update("UPDATE place_manager SET revoked_at = now(), revoked_reason = 'ADMIN' "
                + "WHERE location_id = ? AND user_id = ?", placeId, proId);
        assertThat(call(HttpMethod.GET, "/api/v1/activities/" + activityId, pro, null, 200).get("hostPlace")
                .isNull()).isTrue();
    }

    // ---------------------------------------------------------------- outils

    private static Map<String, Object> activity(long hostPlaceId) {
        return Map.of("title", "Visite guidée " + UUID.randomUUID(), "description", "Découverte",
                "address", "1 rue de la République, Avignon", "startDate", "2030-06-01T10:00:00",
                "endDate", "2030-06-01T12:00:00", "categoryCodes", List.of("CULTURE"), "hostPlaceId", hostPlaceId);
    }

    /** Demande d'offre Pro puis validation sans échéance ; renvoie l'id de l'abonnement. */
    private long approvePro(long userId, String userToken, String admin) throws Exception {
        long id = call(HttpMethod.POST, ME + "/subscription/subscribe", userToken, Map.of("plan", "PRO"), 200)
                .get("pending").get("id").asLong();
        call(HttpMethod.POST, "/api/v1/admin/subscriptions/" + id + "/approve", admin, Map.of(), 200);
        return id;
    }

    private long currentSubscription(long userId) {
        return jdbcTemplate.queryForObject("SELECT id FROM subscription WHERE user_id = ? "
                + "AND status IN ('ACTIVE', 'CANCELED')", Long.class, userId);
    }

    private long publishedTruck(String owner, String admin) throws Exception {
        long truck = call(HttpMethod.POST, "/api/v1/pro/foodtrucks", owner,
                Map.of("name", "Camion " + UUID.randomUUID(), "category", "burger"), 201).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/pro/foodtrucks/" + truck + "/publish", owner, null, 200);
        call(HttpMethod.POST, "/api/v1/admin/foodtrucks/" + truck + "/approve", admin, null, 200);
        return truck;
    }

    private String roleState(long userId) {
        return jdbcTemplate.queryForObject("SELECT role || '/' || COALESCE(pro_role_source, '-') FROM users "
                + "WHERE id = ?", String.class, userId);
    }

    private boolean managerActive(long placeId, long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM place_manager "
                + "WHERE location_id = ? AND user_id = ? AND revoked_at IS NULL)", Boolean.class, placeId, userId));
    }

    private void grantManager(long placeId, long userId) {
        jdbcTemplate.update("INSERT INTO place_manager (location_id, user_id) VALUES (?, ?)", placeId, userId);
    }

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec spec = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
                + "VALUES (?, ?, ?, now(), true) RETURNING id", Long.class, "pr-" + unique,
                "pr-" + unique + "@example.com", role);
        userIds.add(id);
        return id;
    }

    private long place(String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO location (name, latitude, longitude, timezone, place_type, "
                + "place_status, city) VALUES (?, ?, ?, 'Europe/Paris', 'CoveredMarket', ?, 'Avignon') RETURNING id",
                Long.class, "Halle " + UUID.randomUUID(), 43 + ThreadLocalRandom.current().nextDouble(),
                5 + ThreadLocalRandom.current().nextDouble(), status);
        locationIds.add(id);
        return id;
    }

    private String token(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "pr-test@example.com").claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
