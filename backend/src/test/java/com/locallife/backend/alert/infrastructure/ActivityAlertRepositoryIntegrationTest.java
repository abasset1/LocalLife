package com.locallife.backend.alert.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.domain.AlertActivity;
import com.locallife.backend.alert.domain.AlertFoodTruck;
import com.locallife.backend.alert.domain.AlertRecipient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requête de correspondance des alertes contre la base réelle (PostGIS),
 * chaque test annulé en fin de transaction. Activités sans {@code schedule}
 * (branche de repli sur {@code activity.location}) autour de Lyon.
 */
@SpringBootTest
@Transactional
class ActivityAlertRepositoryIntegrationTest {

    private static final double LYON_LATITUDE = 45.7578;
    private static final double LYON_LONGITUDE = 4.8320;

    @Autowired
    private ActivityAlertRepository activityAlertRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, role, created_at, email_verified) "
                        + "VALUES ('alerts', 'alerts-repository@example.com', 'USER', now(), true) RETURNING id",
                Long.class);
    }

    private Long insertActivity(String title, double latitude, double longitude, String categoryCode,
            String status, LocalDateTime startDate) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, latitude, longitude, status, start_date, source_id) "
                        + "VALUES (?, ?, ?, ?, ?, (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                Long.class, title, latitude, longitude, status, startDate);
        jdbcTemplate.update(
                "INSERT INTO activity_category (activity_id, category_id) SELECT ?, id FROM category WHERE code = ?",
                id, categoryCode);
        return id;
    }

    /**
     * Alerte créée « il y a une heure » : dans la transaction du test, {@code now()} est le même
     * instant pour l'alerte et pour les activités publiées ensuite, alors que la règle compare
     * strictement {@code published_at > created_at}.
     */
    private ActivityAlert createAlert(List<String> categoryCodes, int radiusKm) {
        ActivityAlert alert = activityAlertRepository.create(new ActivityAlert(
                null, userId, "Lyon", LYON_LATITUDE, LYON_LONGITUDE, radiusKm, categoryCodes, false, true, false, null),
                "token-" + radiusKm);
        jdbcTemplate.update(
                "UPDATE activity_alert SET created_at = now() - interval '1 hour' WHERE id = ?", alert.id());
        return alert;
    }

    @Test
    void create_ShouldPersistAlertWithCategories() {
        ActivityAlert created = createAlert(List.of("CULTURE", "SPORT"), 10);

        assertThat(created.id()).isNotNull();
        assertThat(created.categoryCodes()).containsExactly("CULTURE", "SPORT");
        assertThat(activityAlertRepository.findByUserId(userId)).extracting(ActivityAlert::id).containsExactly(created.id());
        assertThat(activityAlertRepository.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    void findNewMatchingActivities_ShouldMatchCategoryRadiusAndNewness() {
        LocalDateTime nextWeek = LocalDateTime.now().plusDays(7);
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);
        // Publiée la veille : la date de publication, une fois posée, n'est plus modifiable (trigger V54).
        Long before = insertActivity("Publiée avant l'alerte", LYON_LATITUDE, LYON_LONGITUDE, "SPORT",
                "PENDING", nextWeek);
        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED', published_at = now() - interval '1 day' "
                + "WHERE id = ?", before);

        Long match = insertActivity("Match à Villeurbanne", 45.7719, 4.8902, "SPORT", "PUBLISHED", nextWeek);
        insertActivity("Trop loin (Grenoble)", 45.1885, 5.7245, "SPORT", "PUBLISHED", nextWeek);
        insertActivity("Autre catégorie", LYON_LATITUDE, LYON_LONGITUDE, "CULTURE", "PUBLISHED", nextWeek);
        insertActivity("En attente de modération", LYON_LATITUDE, LYON_LONGITUDE, "SPORT", "PENDING", nextWeek);
        insertActivity("Déjà passée", LYON_LATITUDE, LYON_LONGITUDE, "SPORT", "PUBLISHED",
                LocalDateTime.now().minusDays(3));

        List<AlertActivity> found = activityAlertRepository.findNewMatchingActivities(alert.id(), 100);

        assertThat(found).extracting(AlertActivity::id).containsExactly(match).doesNotContain(before);
    }

    @Test
    void markSent_ShouldExcludeAlreadySentActivities() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);
        Long match = insertActivity("Match", LYON_LATITUDE, LYON_LONGITUDE, "SPORT", "PUBLISHED",
                LocalDateTime.now().plusDays(2));

        activityAlertRepository.markSent(alert.id(), List.of(match));

        assertThat(activityAlertRepository.findNewMatchingActivities(alert.id(), 100)).isEmpty();
    }

    @Test
    void findAllRecipients_ShouldOnlyIncludeVerifiedEmails() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);

        assertThat(activityAlertRepository.findAllRecipients())
                .filteredOn(recipient -> recipient.alert().id().equals(alert.id()))
                .extracting(AlertRecipient::email, AlertRecipient::unsubscribeToken)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("alerts-repository@example.com", "token-10"));

        jdbcTemplate.update("UPDATE users SET email_verified = false WHERE id = ?", userId);
        assertThat(activityAlertRepository.findAllRecipients())
                .noneMatch(recipient -> recipient.alert().id().equals(alert.id()));
    }

    @Test
    void deletes_ShouldRespectOwnerAndToken() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);

        assertThat(activityAlertRepository.deleteByIdAndUserId(alert.id(), userId + 1)).isFalse();
        assertThat(activityAlertRepository.deleteByUnsubscribeToken("wrong")).isFalse();
        assertThat(activityAlertRepository.deleteByUnsubscribeToken("token-10")).isTrue();
        assertThat(activityAlertRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void findAllRecipients_ShouldIncludePushAlerts_OnlyWithRegisteredDevice() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);
        jdbcTemplate.update("UPDATE users SET email_verified = false WHERE id = ?", userId);
        assertThat(activityAlertRepository.updateChannels(alert.id(), userId, false, true)).isTrue();

        assertThat(activityAlertRepository.findAllRecipients())
                .noneMatch(recipient -> recipient.alert().id().equals(alert.id()));

        jdbcTemplate.update("INSERT INTO user_push_token (token, user_id) VALUES ('ExponentPushToken[abc]', ?)", userId);
        assertThat(activityAlertRepository.findAllRecipients())
                .filteredOn(recipient -> recipient.alert().id().equals(alert.id()))
                .singleElement()
                .satisfies(recipient -> {
                    assertThat(recipient.email()).isNull();
                    assertThat(recipient.alert().notifyPush()).isTrue();
                    assertThat(recipient.alert().notifyEmail()).isFalse();
                });
    }

    @Test
    void updateChannels_ShouldRespectOwner() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);

        assertThat(activityAlertRepository.updateChannels(alert.id(), userId + 1, false, true)).isFalse();
    }

    @Test
    void findNewMatchingActivities_ShouldIncludeContributionSubmittedBeforeButPublishedAfterAlert() {
        // Contribution en attente de modération avant la création de l'alerte...
        Long contribution = insertActivity("Contribution validée plus tard", LYON_LATITUDE, LYON_LONGITUDE,
                "SPORT", "PENDING", LocalDateTime.now().plusDays(4));
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);
        // ...dont l'id est donc antérieur au repère min_activity_id de l'alerte.
        jdbcTemplate.update("UPDATE activity_alert SET min_activity_id = ? WHERE id = ?", contribution, alert.id());
        assertThat(activityAlertRepository.findNewMatchingActivities(alert.id(), 100)).isEmpty();

        // Validée après la création de l'alerte : sa date de publication la rend « nouvelle ».
        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED' WHERE id = ?", contribution);

        assertThat(activityAlertRepository.findNewMatchingActivities(alert.id(), 100))
                .extracting(AlertActivity::id).containsExactly(contribution);
    }

    /** Food truck publié, présent (ADR-0013) à un emplacement de la position donnée. */
    private Long insertFoodTruck(String name, double latitude, double longitude) {
        return insertFoodTruck(name, latitude, longitude, "PUBLISHED", null);
    }

    private Long insertFoodTruck(String name, double latitude, double longitude, String presenceStatus,
            String validUntil) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO food_truck (name, category, status) VALUES (?, 'Burgers', 'PUBLISHED') RETURNING id",
                Long.class, name);
        Long spot = jdbcTemplate.queryForObject("INSERT INTO location (latitude, longitude, timezone) "
                + "VALUES (?, ?, 'Europe/Paris') RETURNING id", Long.class, latitude, longitude);
        jdbcTemplate.update("INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, status, "
                + "valid_until) VALUES (?, ?, 'OTHER', ?, CAST(? AS date))", id, spot, presenceStatus, validUntil);
        return id;
    }

    @Test
    void findNewMatchingFoodTrucks_ShouldMatchRadiusNewnessAndAlertOption() {
        ActivityAlert alert = createAlert(List.of("SPORT"), 10);
        // Publié la veille (date fournie à l'insertion : le trigger ne la remplace pas).
        jdbcTemplate.update("INSERT INTO food_truck (name, latitude, longitude, category, status, published_at) "
                + "VALUES ('Publié avant l''alerte', ?, ?, 'Burgers', 'PUBLISHED', now() - interval '1 day')",
                LYON_LATITUDE, LYON_LONGITUDE);
        Long nearby = insertFoodTruck("Le Camion Bleu", 45.7719, 4.8902);
        insertFoodTruck("Trop loin (Grenoble)", 45.1885, 5.7245);
        // La position compte par les présences : une présence suspendue ou terminée n'est pas un emplacement.
        insertFoodTruck("Présence suspendue", 45.7719, 4.8902, "SUSPENDED", null);
        insertFoodTruck("Présence terminée", 45.7719, 4.8902, "PUBLISHED", "2020-01-01");

        // Alerte sans l'option food trucks : rien.
        assertThat(activityAlertRepository.findNewMatchingFoodTrucks(alert.id(), 100)).isEmpty();

        jdbcTemplate.update("UPDATE activity_alert SET include_food_trucks = true WHERE id = ?", alert.id());
        assertThat(activityAlertRepository.findNewMatchingFoodTrucks(alert.id(), 100))
                .extracting(AlertFoodTruck::id).containsExactly(nearby);

        activityAlertRepository.markFoodTrucksSent(alert.id(), List.of(nearby));
        assertThat(activityAlertRepository.findNewMatchingFoodTrucks(alert.id(), 100)).isEmpty();
    }

    @Test
    void foodTruckPublishedAt_ShouldBeSetByTrigger_AndKept() {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO food_truck (name, latitude, longitude, category, status) "
                        + "VALUES ('Brouillon', 45.0, 4.0, 'Crêpes', 'PENDING') RETURNING id", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT published_at FROM food_truck WHERE id = ?",
                java.sql.Timestamp.class, id)).isNull();

        jdbcTemplate.update("UPDATE food_truck SET status = 'PUBLISHED', published_at = '2026-01-01 10:00' WHERE id = ?",
                id);
        jdbcTemplate.update("UPDATE food_truck SET published_at = NULL WHERE id = ?", id);

        assertThat(jdbcTemplate.queryForObject("SELECT published_at FROM food_truck WHERE id = ?",
                java.sql.Timestamp.class, id).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

}
