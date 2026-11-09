package com.locallife.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.follow.application.FollowNotifier;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.infrastructure.FollowRepository;
import com.locallife.backend.push.application.ExpoPushService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Cloche « Suivre » (V79, V91) contre la base réelle : réservée à Premium et Pro, triggers qui enregistrent les
 * changements des activités, lieux et food trucks suivis, nouvelles activités d'un lieu suivi, présence du jour d'un
 * food truck suivi, envoi du récapitulatif.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FollowIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FollowNotifier followNotifier;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ExpoPushService expoPushService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();
    private final List<Long> foodTruckIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM follow WHERE user_id = ?", id));
        activityIds.forEach(id -> {
            jdbcTemplate.update("DELETE FROM occurrence WHERE schedule_id IN (SELECT id FROM schedule "
                    + "WHERE activity_id = ?)", id);
            jdbcTemplate.update("DELETE FROM schedule WHERE activity_id = ?", id);
            jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
        });
        foodTruckIds.forEach(id -> jdbcTemplate.update("DELETE FROM food_truck WHERE id = ?", id));
        locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
        userIds.forEach(id -> {
            jdbcTemplate.update("DELETE FROM subscription WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    void followingIsForPremiumAndPro_AndUnfollowingAlwaysWorks() throws Exception {
        long userId = user();
        String token = token(userId);
        long activity = publishedActivity();
        long place = publishedPlace();

        assertThat(call(HttpMethod.GET, "/api/v1/users/me", token, 200).get("limits").get("canFollow").asBoolean())
                .isFalse();
        assertThat(call(HttpMethod.POST, "/api/v1/activities/" + activity + "/follow", token, 403)
                .get("message").asText()).contains("Premium et Pro");

        grantPremium(userId);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", token, 200).get("limits").get("canFollow").asBoolean())
                .isTrue();
        call(HttpMethod.POST, "/api/v1/activities/" + activity + "/follow", token, 200);
        call(HttpMethod.POST, "/api/v1/activities/" + activity + "/follow", token, 200);
        call(HttpMethod.POST, "/api/v1/places/" + place + "/follow", token, 200);
        call(HttpMethod.POST, "/api/v1/activities/999999999/follow", token, 404);

        JsonNode follows = call(HttpMethod.GET, "/api/v1/users/me/follows", token, 200);
        assertThat(follows).hasSize(2);
        assertThat(follows.findValuesAsText("type")).containsExactlyInAnyOrder("activity", "place");

        // Revenu au gratuit : ne peut plus en ajouter, mais retire les siens.
        jdbcTemplate.update("UPDATE subscription SET status = 'EXPIRED' WHERE user_id = ?", userId);
        call(HttpMethod.POST, "/api/v1/activities/" + publishedActivity() + "/follow", token, 403);
        call(HttpMethod.DELETE, "/api/v1/activities/" + activity + "/follow", token, 200);
        call(HttpMethod.DELETE, "/api/v1/places/" + place + "/follow", token, 200);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me/follows", token, 200)).isEmpty();
    }

    @Test
    void triggers_ShouldRecordVisibleChangesOfFollowedTargetsOnly() {
        long userId = user();
        long followed = publishedActivity();
        long notFollowed = publishedActivity();
        long place = publishedPlace();
        followRepository.insert(userId, FollowTargetType.ACTIVITY, followed);
        followRepository.insert(userId, FollowTargetType.PLACE, place);

        jdbcTemplate.update("UPDATE activity SET start_date = start_date + interval '1 day', address = '2 rue Neuve' "
                + "WHERE id IN (?, ?)", followed, notFollowed);
        jdbcTemplate.update("UPDATE location SET phone = '04 00 00 00 00' WHERE id = ?", place);

        assertThat(kinds(followed)).containsExactly("DATES", "LOCATION");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM follow_change WHERE activity_id = ?",
                Long.class, notFollowed)).isZero();

        // Annulation, puis contribution repassée en attente de relecture : seule l'annulation compte.
        jdbcTemplate.update("UPDATE activity SET status = 'CANCELLED' WHERE id = ?", followed);
        jdbcTemplate.update("UPDATE activity SET status = 'PENDING', title = 'Nouveau titre' WHERE id = ?", followed);

        List<FollowUpdate> updates = followRepository.findPendingUpdates(userId, 10);
        assertThat(updates).extracting(FollowUpdate::type)
                .containsExactlyInAnyOrder(FollowTargetType.ACTIVITY, FollowTargetType.PLACE);
        FollowUpdate activityUpdate = updates.stream()
                .filter(update -> update.type() == FollowTargetType.ACTIVITY).findFirst().orElseThrow();
        assertThat(activityUpdate.kinds()).containsExactly("CANCELLED", "DATES", "LOCATION");
        FollowUpdate placeUpdate = updates.stream()
                .filter(update -> update.type() == FollowTargetType.PLACE).findFirst().orElseThrow();
        assertThat(placeUpdate.kinds()).containsExactly("CONTACT");

        // Une fois notifié, plus rien jusqu'au prochain changement.
        updates.forEach(update -> followRepository.markNotified(
                update.followId(), update.maxChangeId(), java.time.OffsetDateTime.now()));
        assertThat(followRepository.findPendingUpdates(userId, 10)).isEmpty();
    }

    @Test
    void aFollowedPlace_ShouldReportActivitiesPublishedThereSinceTheLastSend() {
        long userId = user();
        long place = publishedPlace();
        followRepository.insert(userId, FollowTargetType.PLACE, place);
        jdbcTemplate.update("UPDATE follow SET notified_until = now() - interval '1 hour' WHERE user_id = ?", userId);

        long atPlace = publishedActivity();
        jdbcTemplate.update("INSERT INTO schedule (activity_id, location_id) VALUES (?, ?)", atPlace, place);
        long elsewhere = publishedActivity();

        List<FollowUpdate> updates = followRepository.findPendingUpdates(userId, 10);
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).newActivities()).containsExactly(title(atPlace));
        assertThat(updates.get(0).newActivities()).doesNotContain(title(elsewhere));
    }

    @Test
    void notifier_ShouldEmailAPremiumUserWithoutDevice_AndSkipAFreeOne() {
        long premium = user();
        grantPremium(premium);
        long free = user();
        long activity = publishedActivity();
        followRepository.insert(premium, FollowTargetType.ACTIVITY, activity);
        followRepository.insert(free, FollowTargetType.ACTIVITY, activity);

        jdbcTemplate.update("UPDATE activity SET status = 'CANCELLED' WHERE id = ?", activity);
        followNotifier.sendAll();

        verify(emailService).sendActivityAlertEmail(eq(email(premium)), contains(title(activity)), contains("annulée"));
        verify(emailService, never()).sendActivityAlertEmail(eq(email(free)), anyString(), anyString());
        verify(expoPushService, never()).send(any(), anyString(), anyString(), any());
        // Rien de nouveau : pas de second envoi, et rien d'arriéré pour le compte gratuit.
        assertThat(followRepository.findPendingUpdates(premium, 10)).isEmpty();
        assertThat(followRepository.findPendingUpdates(free, 10)).isEmpty();
    }

    @Test
    void aFoodTruck_CanBeFollowedByPremium_AndAppearsInTheList() throws Exception {
        long userId = user();
        String token = token(userId);
        long foodTruck = publishedFoodTruck();

        call(HttpMethod.POST, "/api/v1/foodtrucks/" + foodTruck + "/follow", token, 403);
        grantPremium(userId);
        call(HttpMethod.POST, "/api/v1/foodtrucks/" + foodTruck + "/follow", token, 200);
        call(HttpMethod.POST, "/api/v1/foodtrucks/999999999/follow", token, 404);

        JsonNode follows = call(HttpMethod.GET, "/api/v1/users/me/follows", token, 200);
        assertThat(follows).hasSize(1);
        assertThat(follows.get(0).get("type").asText()).isEqualTo("foodtruck");
        assertThat(follows.get(0).get("id").asLong()).isEqualTo(foodTruck);

        call(HttpMethod.DELETE, "/api/v1/foodtrucks/" + foodTruck + "/follow", token, 200);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me/follows", token, 200)).isEmpty();
    }

    @Test
    void aFollowedFoodTruck_ShouldReportProfileAndPlanningChanges() {
        long userId = user();
        long foodTruck = publishedFoodTruck();
        followRepository.insert(userId, FollowTargetType.FOOD_TRUCK, foodTruck);

        jdbcTemplate.update("UPDATE food_truck SET instagram_url = 'https://instagram.com/x' WHERE id = ?", foodTruck);
        presence(foodTruck, "SA", "11:30", "14:00", null);

        List<FollowUpdate> updates = followRepository.findPendingUpdates(userId, 10);
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).type()).isEqualTo(FollowTargetType.FOOD_TRUCK);
        assertThat(updates.get(0).kinds()).containsExactly("CONTACT", "PLANNING");
    }

    @Test
    void aFollowedFoodTruck_ShouldBeAnnouncedOnceOnTheDayItIsSomewhere() {
        long userId = user();
        long foodTruck = publishedFoodTruck();
        followRepository.insert(userId, FollowTargetType.FOOD_TRUCK, foodTruck);
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).getDayOfWeek().name()
                .substring(0, 2);
        presence(foodTruck, today, "11:30", "14:00", "Place du marché");
        // Présence permanente : pas une nouvelle du jour.
        presence(foodTruck, null, null, null, "Dépôt");
        // Suivi d'aujourd'hui : rien n'est annoncé avant demain ; on simule un suivi plus ancien.
        jdbcTemplate.update("UPDATE follow SET presence_notified_on = NULL, last_change_id = "
                + "(SELECT COALESCE(max(id), 0) FROM follow_change) WHERE user_id = ?", userId);

        List<FollowUpdate> updates = followRepository.findPendingUpdates(userId, 10);
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).presentToday()).containsExactly("Place du marché, 11:30–14:00");

        followRepository.markPresenceNotified(updates.get(0).followId());
        assertThat(followRepository.findPendingUpdates(userId, 10)).isEmpty();
    }

    // ---------------------------------------------------------------- outils

    private List<String> kinds(long activityId) {
        return jdbcTemplate.queryForList("SELECT unnest(kinds) FROM follow_change WHERE activity_id = ? ORDER BY 1",
                String.class, activityId);
    }

    private String title(long activityId) {
        return jdbcTemplate.queryForObject("SELECT title FROM activity WHERE id = ?", String.class, activityId);
    }

    private String email(long userId) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    private long publishedActivity() {
        Long id = jdbcTemplate.queryForObject("""
                WITH a AS (
                    INSERT INTO activity (title, status, latitude, longitude, start_date, published_at, source_id)
                    VALUES (?, 'PUBLISHED', 45.76, 4.83, now() + interval '2 days', now(),
                            (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1))
                    RETURNING id
                ), c AS (
                    INSERT INTO activity_category (activity_id, category_id)
                    SELECT a.id, (SELECT id FROM category WHERE code = 'CULTURE') FROM a
                )
                SELECT id FROM a
                """, Long.class, "Concert " + UUID.randomUUID());
        activityIds.add(id);
        return id;
    }

    private long publishedPlace() {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO location (name, city, latitude, longitude, place_type, place_status, published_at)
                VALUES (?, 'Lyon', 45.76, 4.83, 'MUSEUM', 'PUBLISHED', now())
                RETURNING id
                """, Long.class, "Musée " + UUID.randomUUID());
        locationIds.add(id);
        return id;
    }

    private long publishedFoodTruck() {
        Long id = jdbcTemplate.queryForObject("INSERT INTO food_truck (name, category, status) "
                + "VALUES (?, 'Burgers', 'PUBLISHED') RETURNING id", Long.class, "Camion " + UUID.randomUUID());
        foodTruckIds.add(id);
        return id;
    }

    private void presence(long foodTruckId, String days, String start, String end, String spotName) {
        Long spot = jdbcTemplate.queryForObject("INSERT INTO location (name, city, latitude, longitude, country_code, "
                + "timezone) VALUES (?, 'Lyon', 45.76, 4.83, 'FR', 'Europe/Paris') RETURNING id", Long.class, spotName);
        locationIds.add(spot);
        jdbcTemplate.update("INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, days_of_week, "
                + "start_time, end_time, status) VALUES (?, ?, 'MARKET', ?, ?::time, ?::time, 'PUBLISHED')",
                foodTruckId, spot, days, start, end);
    }

    private void grantPremium(long userId) {
        jdbcTemplate.update("INSERT INTO subscription (user_id, plan, status, provider, current_period_start) "
                + "VALUES (?, 'PREMIUM', 'ACTIVE', 'SIMULATED', now())", userId);
    }

    private JsonNode call(HttpMethod method, String uri, String token, int expectedStatus) throws Exception {
        String response = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : OBJECT_MAPPER.readTree(response);
    }

    private long user() {
        String unique = UUID.randomUUID().toString().substring(0, 12);
        Long id = jdbcTemplate.queryForObject("INSERT INTO users (username, email, role, created_at, email_verified) "
                + "VALUES (?, ?, 'USER', now(), true) RETURNING id", Long.class, "fo-" + unique,
                "fo-" + unique + "@example.com");
        userIds.add(id);
        return id;
    }

    private String token(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId).claim("email", "fo-test@example.com").claim("role", "USER")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
