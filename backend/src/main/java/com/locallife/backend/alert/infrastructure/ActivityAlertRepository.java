package com.locallife.backend.alert.infrastructure;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.domain.AlertActivity;
import com.locallife.backend.alert.domain.AlertFoodTruck;
import com.locallife.backend.alert.domain.AlertRecipient;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux tables d'alertes (V52), en {@link JdbcTemplate} : la recherche
 * des activités correspondantes est une requête PostGIS sur mesure, et les
 * catégories d'une alerte sont une simple table de jointure.
 */
@Repository
public class ActivityAlertRepository {

    private static final String ALERT_COLUMNS =
            "al.id, al.user_id, al.city, al.latitude, al.longitude, al.radius_km, al.include_food_trucks, "
                    + "al.notify_email, al.notify_push, al.created_at";

    /**
     * Nouvelles activités publiées correspondant à l'alerte {@code ?} :
     * <ul>
     *   <li>publiées après la création de l'alerte ({@code published_at}, V54 — y compris une
     *       contribution soumise avant l'alerte mais validée après), jamais envoyées pour elle, et pas
     *       créées par le destinataire lui-même. Activité publiée avant V54 (date inconnue) : repli sur
     *       {@code min_activity_id} ;</li>
     *   <li>portant au moins une des catégories de l'alerte ({@code activity_category}) ;</li>
     *   <li>à venir et dans le rayon : même double branche que {@code ActivityRepository#findWithinRadius}
     *       (occurrences non annulées et leur lieu effectif, sinon repli sur les colonnes
     *       d'{@code activity} pour une activité sans {@code schedule}), sans sa fenêtre de 7 jours.</li>
     * </ul>
     */
    private static final String NEW_MATCHING_ACTIVITIES = """
            SELECT a.id, a.title, a.city, a.start_date, a.coordinate_precision
            FROM activity_alert al
            JOIN activity a ON (a.published_at > al.created_at
                                OR (a.published_at IS NULL AND a.id > al.min_activity_id))
            WHERE al.id = ?
              AND a.status = 'PUBLISHED'
              AND (a.created_by IS NULL OR a.created_by <> al.user_id)
              AND NOT EXISTS (
                  SELECT 1 FROM activity_alert_sent sent WHERE sent.alert_id = al.id AND sent.activity_id = a.id)
              AND EXISTS (
                  SELECT 1 FROM activity_category ac
                  JOIN category c ON c.id = ac.category_id
                  JOIN activity_alert_category aac ON aac.category_code = c.code AND aac.alert_id = al.id
                  WHERE ac.activity_id = a.id)
              AND (
                  EXISTS (
                      SELECT 1 FROM schedule s
                      JOIN occurrence o ON o.schedule_id = s.id
                      JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                      WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                        AND COALESCE(o.end_at, o.start_at) >= now()
                        AND l.geo_location IS NOT NULL
                        AND ST_DWithin(l.geo_location,
                              ST_SetSRID(ST_MakePoint(al.longitude, al.latitude), 4326)::geography,
                              al.radius_km * 1000))
                  OR (
                      NOT EXISTS (SELECT 1 FROM schedule s2 WHERE s2.activity_id = a.id)
                      AND a.location IS NOT NULL
                      AND ST_DWithin(a.location,
                            ST_SetSRID(ST_MakePoint(al.longitude, al.latitude), 4326)::geography,
                            al.radius_km * 1000)
                      AND (COALESCE(a.end_date, a.start_date) IS NULL
                           OR COALESCE(a.end_date, a.start_date) >= CURRENT_DATE)))
            ORDER BY a.start_date NULLS LAST, a.id
            LIMIT ?
            """;

    /**
     * Nouveaux food trucks correspondant à l'alerte {@code ?} : alerte couvrant les food trucks,
     * food truck publié après la création de l'alerte (V55 ; {@code published_at} inconnu pour les
     * food trucks antérieurs, jamais envoyés), jamais envoyé pour elle, et dans le rayon.
     */
    private static final String NEW_MATCHING_FOOD_TRUCKS = """
            SELECT ft.id, ft.name, ft.category
            FROM activity_alert al
            JOIN food_truck ft ON ft.published_at > al.created_at
            WHERE al.id = ?
              AND al.include_food_trucks
              AND ft.status = 'PUBLISHED'
              AND NOT EXISTS (
                  SELECT 1 FROM activity_alert_food_truck_sent sent
                  WHERE sent.alert_id = al.id AND sent.food_truck_id = ft.id)
              AND ST_DWithin(ST_SetSRID(ST_MakePoint(ft.longitude, ft.latitude), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(al.longitude, al.latitude), 4326)::geography,
                    al.radius_km * 1000)
            ORDER BY ft.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ActivityAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ActivityAlert> findByUserId(Long userId) {
        return withCategories(jdbcTemplate.query(
                "SELECT " + ALERT_COLUMNS + " FROM activity_alert al WHERE al.user_id = ? ORDER BY al.id",
                (resultSet, rowNumber) -> mapAlert(resultSet), userId));
    }

    public int countByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_alert WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** Crée l'alerte : seules les activités publiées après ce moment lui seront envoyées. */
    public ActivityAlert create(ActivityAlert alert, String unsubscribeToken) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity_alert "
                        + "(user_id, city, latitude, longitude, radius_km, include_food_trucks, notify_email, "
                        + "notify_push, min_activity_id, unsubscribe_token) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(id), 0) FROM activity), ?) "
                        + "RETURNING id",
                Long.class, alert.userId(), alert.city(), alert.latitude(), alert.longitude(), alert.radiusKm(),
                alert.includeFoodTrucks(), alert.notifyEmail(), alert.notifyPush(), unsubscribeToken);
        jdbcTemplate.batchUpdate(
                "INSERT INTO activity_alert_category (alert_id, category_code) VALUES (?, ?)",
                alert.categoryCodes().stream().map(code -> new Object[] {id, code}).toList());
        return findByUserId(alert.userId()).stream()
                .filter(created -> created.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    /** @return {@code false} si l'alerte n'existe pas ou appartient à un autre utilisateur */
    public boolean updateChannels(Long id, Long userId, boolean notifyEmail, boolean notifyPush) {
        return jdbcTemplate.update(
                "UPDATE activity_alert SET notify_email = ?, notify_push = ? WHERE id = ? AND user_id = ?",
                notifyEmail, notifyPush, id, userId) > 0;
    }

    /** @return {@code false} si l'alerte n'existe pas ou appartient à un autre utilisateur */
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return jdbcTemplate.update("DELETE FROM activity_alert WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    /** @return {@code false} si aucune alerte ne porte ce jeton (lien déjà utilisé ou invalide) */
    public boolean deleteByUnsubscribeToken(String token) {
        return jdbcTemplate.update("DELETE FROM activity_alert WHERE unsubscribe_token = ?", token) > 0;
    }

    /**
     * Alertes ayant au moins un canal utilisable : e-mail vers une adresse
     * vérifiée, ou notification vers au moins un appareil enregistré.
     */
    public List<AlertRecipient> findAllRecipients() {
        List<AlertRecipient> recipients = jdbcTemplate.query(
                "SELECT " + ALERT_COLUMNS + ", CASE WHEN u.email_verified THEN u.email END AS email, "
                        + "al.unsubscribe_token FROM activity_alert al JOIN users u ON u.id = al.user_id "
                        + "WHERE (al.notify_email AND u.email_verified) OR (al.notify_push AND EXISTS ("
                        + "SELECT 1 FROM user_push_token t WHERE t.user_id = al.user_id)) ORDER BY al.id",
                (resultSet, rowNumber) -> new AlertRecipient(
                        mapAlert(resultSet), resultSet.getString("email"), resultSet.getString("unsubscribe_token")));
        List<ActivityAlert> alerts = withCategories(recipients.stream().map(AlertRecipient::alert).toList());
        List<AlertRecipient> completed = new ArrayList<>();
        for (int index = 0; index < recipients.size(); index++) {
            AlertRecipient recipient = recipients.get(index);
            completed.add(new AlertRecipient(alerts.get(index), recipient.email(), recipient.unsubscribeToken()));
        }
        return completed;
    }

    public List<AlertActivity> findNewMatchingActivities(Long alertId, int limit) {
        return jdbcTemplate.query(NEW_MATCHING_ACTIVITIES,
                (resultSet, rowNumber) -> {
                    Timestamp startDate = resultSet.getTimestamp("start_date");
                    return new AlertActivity(resultSet.getLong("id"), resultSet.getString("title"),
                            resultSet.getString("city"), startDate == null ? null : startDate.toLocalDateTime(),
                            "APPROXIMATE".equals(resultSet.getString("coordinate_precision")));
                },
                alertId, limit);
    }

    public List<AlertFoodTruck> findNewMatchingFoodTrucks(Long alertId, int limit) {
        return jdbcTemplate.query(NEW_MATCHING_FOOD_TRUCKS,
                (resultSet, rowNumber) -> new AlertFoodTruck(
                        resultSet.getLong("id"), resultSet.getString("name"), resultSet.getString("category")),
                alertId, limit);
    }

    public void markSent(Long alertId, List<Long> activityIds) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO activity_alert_sent (alert_id, activity_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                activityIds.stream().map(activityId -> new Object[] {alertId, activityId}).toList());
    }

    public void markFoodTrucksSent(Long alertId, List<Long> foodTruckIds) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO activity_alert_food_truck_sent (alert_id, food_truck_id) VALUES (?, ?) "
                        + "ON CONFLICT DO NOTHING",
                foodTruckIds.stream().map(foodTruckId -> new Object[] {alertId, foodTruckId}).toList());
    }

    private static ActivityAlert mapAlert(ResultSet resultSet) throws SQLException {
        return new ActivityAlert(
                resultSet.getLong("id"), resultSet.getLong("user_id"), resultSet.getString("city"),
                resultSet.getDouble("latitude"), resultSet.getDouble("longitude"), resultSet.getInt("radius_km"),
                List.of(), resultSet.getBoolean("include_food_trucks"), resultSet.getBoolean("notify_email"),
                resultSet.getBoolean("notify_push"),
                resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    /** Complète les alertes de leurs catégories, en une requête pour toute la liste. */
    private List<ActivityAlert> withCategories(List<ActivityAlert> alerts) {
        if (alerts.isEmpty()) {
            return alerts;
        }
        Map<Long, List<String>> codesByAlert = new HashMap<>();
        String placeholders = String.join(",", alerts.stream().map(alert -> "?").toList());
        jdbcTemplate.query(
                "SELECT alert_id, category_code FROM activity_alert_category WHERE alert_id IN (" + placeholders
                        + ") ORDER BY category_code",
                resultSet -> {
                    codesByAlert.computeIfAbsent(resultSet.getLong("alert_id"), id -> new ArrayList<>())
                            .add(resultSet.getString("category_code"));
                },
                alerts.stream().map(ActivityAlert::id).toArray());
        return alerts.stream()
                .map(alert -> new ActivityAlert(alert.id(), alert.userId(), alert.city(), alert.latitude(),
                        alert.longitude(), alert.radiusKm(), List.copyOf(codesByAlert.getOrDefault(alert.id(),
                        List.of())), alert.includeFoodTrucks(), alert.notifyEmail(), alert.notifyPush(),
                        alert.createdAt()))
                .toList();
    }

}
