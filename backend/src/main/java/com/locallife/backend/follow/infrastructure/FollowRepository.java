package com.locallife.backend.follow.infrastructure;

import com.locallife.backend.follow.domain.FollowRecipient;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.domain.FollowedItem;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux suivis ({@code follow}) et aux changements enregistrés par les triggers ({@code follow_change}, V79 et
 * V91), en {@link JdbcTemplate} comme les alertes.
 */
@Repository
public class FollowRepository {

    /** Dernier changement existant : un nouveau suivi ne notifie jamais ce qui s'est passé avant lui. */
    private static final String LAST_CHANGE = "COALESCE((SELECT max(id) FROM follow_change), 0)";

    private static final String FOLLOWED_ITEMS = """
            SELECT 'ACTIVITY' AS type, a.id AS target_id, a.title, a.city, a.status, f.created_at
            FROM follow f JOIN activity a ON a.id = f.activity_id
            WHERE f.user_id = ?
            UNION ALL
            SELECT 'PLACE', l.id, l.name, l.city, l.place_status, f.created_at
            FROM follow f JOIN location l ON l.id = f.location_id
            WHERE f.user_id = ?
            UNION ALL
            SELECT 'FOOD_TRUCK', ft.id, ft.name, NULL, ft.status, f.created_at
            FROM follow f JOIN food_truck ft ON ft.id = f.food_truck_id
            WHERE f.user_id = ?
            ORDER BY created_at DESC
            """;

    /** Changements de chaque suivi d'un utilisateur depuis le dernier envoi, regroupés par suivi. */
    private static final String PENDING_CHANGES = """
            SELECT f.id AS follow_id, 'ACTIVITY' AS type, a.id AS target_id, a.title,
                   array_agg(DISTINCT k.kind) AS kinds, max(c.id) AS max_change_id
            FROM follow f
            JOIN activity a ON a.id = f.activity_id
            JOIN follow_change c ON c.activity_id = f.activity_id AND c.id > f.last_change_id
            CROSS JOIN LATERAL unnest(c.kinds) AS k(kind)
            WHERE f.user_id = ?
            GROUP BY f.id, a.id, a.title
            UNION ALL
            SELECT f.id, 'PLACE', l.id, l.name, array_agg(DISTINCT k.kind), max(c.id)
            FROM follow f
            JOIN location l ON l.id = f.location_id
            JOIN follow_change c ON c.location_id = f.location_id AND c.id > f.last_change_id
            CROSS JOIN LATERAL unnest(c.kinds) AS k(kind)
            WHERE f.user_id = ?
            GROUP BY f.id, l.id, l.name
            UNION ALL
            SELECT f.id, 'FOOD_TRUCK', ft.id, ft.name, array_agg(DISTINCT k.kind), max(c.id)
            FROM follow f
            JOIN food_truck ft ON ft.id = f.food_truck_id
            JOIN follow_change c ON c.food_truck_id = f.food_truck_id AND c.id > f.last_change_id
            CROSS JOIN LATERAL unnest(c.kinds) AS k(kind)
            WHERE f.user_id = ?
            GROUP BY f.id, ft.id, ft.name
            """;

    /**
     * Activités publiées depuis le dernier envoi dans un lieu suivi : au lieu lui-même ou à un lieu d'événement qui
     * lui est relié, même règle que {@code ActivityAtPlaceRepository} ; à venir, et pas créées par l'abonné.
     */
    private static final String NEW_ACTIVITIES_AT_PLACES = """
            SELECT f.id AS follow_id, l.id AS place_id, l.name AS place_name, a.title
            FROM follow f
            JOIN location l ON l.id = f.location_id AND l.place_status = 'PUBLISHED'
            JOIN LATERAL (
                SELECT DISTINCT a.id, a.title, a.start_date
                FROM activity a
                JOIN schedule s ON s.activity_id = a.id
                LEFT JOIN occurrence o ON o.schedule_id = s.id AND o.status <> 'CANCELLED'
                JOIN location hit ON hit.id IN (s.location_id, o.location_id)
                WHERE (hit.id = l.id OR hit.place_id = l.id)
                  AND a.status = 'PUBLISHED'
                  AND a.published_at > f.notified_until
                  AND (a.created_by IS NULL OR a.created_by <> f.user_id)
                  AND (COALESCE(a.end_date, a.start_date) IS NULL
                       OR COALESCE(a.end_date, a.start_date) >= (now() AT TIME ZONE 'Europe/Paris')::date)
                ORDER BY a.start_date NULLS LAST, a.id
                LIMIT ?
            ) a ON TRUE
            WHERE f.user_id = ?
            ORDER BY f.id, a.start_date NULLS LAST, a.id
            """;

    /**
     * Emplacements du jour des food trucks suivis dont l'abonné n'a pas encore été prévenu aujourd'hui : présence
     * publiée d'un food truck publié, valable ce jour (même règle que {@code FoodTruckPresenceRepository}). Une
     * présence permanente (tous les jours, sans dates) n'est pas une nouvelle : elle n'est pas annoncée chaque matin.
     */
    private static final String PRESENT_TODAY = """
            SELECT f.id AS follow_id, ft.id AS food_truck_id, ft.name,
                   COALESCE(l.name, l.address, l.city) AS spot_name, p.start_time, p.end_time
            FROM follow f
            JOIN food_truck ft ON ft.id = f.food_truck_id AND ft.status = 'PUBLISHED'
            JOIN food_truck_presence p ON p.food_truck_id = ft.id AND p.status = 'PUBLISHED'
            JOIN location l ON l.id = p.location_id
            WHERE f.user_id = ?
              AND (f.presence_notified_on IS NULL OR f.presence_notified_on < ?)
              AND (p.valid_from IS NULL OR p.valid_from <= ?)
              AND (p.valid_until IS NULL OR p.valid_until >= ?)
              AND (p.days_of_week IS NULL OR position(? IN p.days_of_week) > 0)
              AND NOT (p.days_of_week IS NULL AND p.valid_from IS NULL AND p.valid_until IS NULL)
            ORDER BY f.id, p.start_time NULLS FIRST, p.id
            """;

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Activité visible du public (publiée ou annulée) : la seule que l'on peut suivre. */
    public boolean isFollowableActivity(Long activityId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM activity WHERE id = ? AND status IN ('PUBLISHED', 'CANCELLED'))",
                Boolean.class, activityId));
    }

    /** Lieu permanent publié : le seul que l'on peut suivre. */
    public boolean isFollowablePlace(Long placeId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM location WHERE id = ? AND place_status = 'PUBLISHED')",
                Boolean.class, placeId));
    }

    /** Food truck publié : le seul que l'on peut suivre. */
    public boolean isFollowableFoodTruck(Long foodTruckId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM food_truck WHERE id = ? AND status = 'PUBLISHED')",
                Boolean.class, foodTruckId));
    }

    public boolean exists(Long userId, FollowTargetType type, Long targetId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM follow WHERE user_id = ? AND " + column(type) + " = ?)",
                Boolean.class, userId, targetId));
    }

    /**
     * Idempotent : suivre deux fois la même cible ne crée qu'un suivi. Un food truck suivi aujourd'hui n'est annoncé
     * qu'à partir de demain : l'abonné vient de le voir.
     */
    public void insert(Long userId, FollowTargetType type, Long targetId) {
        String column = column(type);
        jdbcTemplate.update("INSERT INTO follow (user_id, " + column + ", last_change_id, presence_notified_on) "
                + "VALUES (?, ?, " + LAST_CHANGE + ", ?) ON CONFLICT (user_id, " + column + ") WHERE " + column
                + " IS NOT NULL DO NOTHING", userId, targetId,
                type == FollowTargetType.FOOD_TRUCK ? today() : null);
    }

    public void delete(Long userId, FollowTargetType type, Long targetId) {
        jdbcTemplate.update("DELETE FROM follow WHERE user_id = ? AND " + column(type) + " = ?", userId, targetId);
    }

    public List<FollowedItem> findByUserId(Long userId) {
        return jdbcTemplate.query(FOLLOWED_ITEMS, (resultSet, rowNumber) -> new FollowedItem(
                FollowTargetType.valueOf(resultSet.getString("type")), resultSet.getLong("target_id"),
                resultSet.getString("title"), resultSet.getString("city"), resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class)), userId, userId, userId);
    }

    /** Utilisateurs qui suivent au moins une activité ou un lieu, avec leur adresse si elle est vérifiée. */
    public List<FollowRecipient> findRecipients() {
        return jdbcTemplate.query("""
                SELECT u.id, CASE WHEN u.email_verified THEN u.email END AS email
                FROM users u WHERE EXISTS (SELECT 1 FROM follow f WHERE f.user_id = u.id) ORDER BY u.id""",
                (resultSet, rowNumber) -> new FollowRecipient(resultSet.getLong("id"), resultSet.getString("email")));
    }

    /**
     * Mises à jour à notifier à un utilisateur : changements depuis le dernier envoi, pour les lieux, au plus
     * {@code maxNewActivitiesPerPlace} nouvelles activités chacun et, pour les food trucks, leurs emplacements du jour
     * pas encore annoncés. Suivis sans rien de nouveau : absents.
     */
    public List<FollowUpdate> findPendingUpdates(Long userId, int maxNewActivitiesPerPlace) {
        Map<Long, FollowUpdate> updates = new LinkedHashMap<>();
        jdbcTemplate.query(PENDING_CHANGES, (ResultSet resultSet) -> {
            long followId = resultSet.getLong("follow_id");
            updates.put(followId, new FollowUpdate(followId, FollowTargetType.valueOf(resultSet.getString("type")),
                    resultSet.getLong("target_id"), resultSet.getString("title"), kinds(resultSet.getArray("kinds")),
                    new ArrayList<>(), resultSet.getLong("max_change_id")));
        }, userId, userId, userId);
        jdbcTemplate.query(NEW_ACTIVITIES_AT_PLACES, (ResultSet resultSet) -> {
            long followId = resultSet.getLong("follow_id");
            long placeId = resultSet.getLong("place_id");
            String placeName = resultSet.getString("place_name");
            FollowUpdate update = updates.computeIfAbsent(followId, id -> new FollowUpdate(
                    id, FollowTargetType.PLACE, placeId, placeName, List.of(), new ArrayList<>(), 0));
            update.newActivities().add(resultSet.getString("title"));
        }, maxNewActivitiesPerPlace, userId);
        LocalDate today = today();
        jdbcTemplate.query(PRESENT_TODAY, (ResultSet resultSet) -> {
            long followId = resultSet.getLong("follow_id");
            long foodTruckId = resultSet.getLong("food_truck_id");
            String name = resultSet.getString("name");
            FollowUpdate update = updates.computeIfAbsent(followId, id -> new FollowUpdate(
                    id, FollowTargetType.FOOD_TRUCK, foodTruckId, name, List.of(), List.of(), 0, new ArrayList<>()));
            update.presentToday().add(spot(resultSet));
        }, userId, today, today, today, dayCode(today));
        return List.copyOf(updates.values());
    }

    /**
     * Après un envoi (ou pour un compte qui n'a plus droit au suivi) : tout ce qui est antérieur à {@code until} est
     * considéré comme notifié. {@code lastChangeId} ne recule jamais.
     */
    public void markNotified(Long followId, long lastChangeId, OffsetDateTime until) {
        jdbcTemplate.update("UPDATE follow SET last_change_id = GREATEST(last_change_id, ?), notified_until = ? "
                + "WHERE id = ?", lastChangeId, until, followId);
    }

    /** Après l'annonce des emplacements du jour d'un food truck suivi : plus rien à annoncer avant demain. */
    public void markPresenceNotified(Long followId) {
        jdbcTemplate.update("UPDATE follow SET presence_notified_on = ? WHERE id = ?", today(), followId);
    }

    /** Pour un compte sans droit au suivi : rien de ce qui précède {@code until} ne lui sera envoyé plus tard. */
    public void markAllNotified(Long userId, OffsetDateTime until) {
        jdbcTemplate.update("UPDATE follow SET last_change_id = " + LAST_CHANGE + ", notified_until = ?, "
                + "presence_notified_on = CASE WHEN food_truck_id IS NULL THEN NULL ELSE ? END "
                + "WHERE user_id = ?", until, today(), userId);
    }

    /** Changements de plus de {@code days} jours : déjà notifiés ou trop anciens pour l'être. */
    public int purgeChangesOlderThan(int days) {
        return jdbcTemplate.update("DELETE FROM follow_change WHERE changed_at < now() - make_interval(days => ?)",
                days);
    }

    private static String column(FollowTargetType type) {
        return switch (type) {
            case ACTIVITY -> "activity_id";
            case PLACE -> "location_id";
            case FOOD_TRUCK -> "food_truck_id";
        };
    }

    /** Jour courant à Paris, comme les présences des food trucks. */
    private static LocalDate today() {
        return LocalDate.now(PARIS);
    }

    /** Code du jour dans {@code food_truck_presence.days_of_week} : {@code MO}…{@code SU}. */
    private static String dayCode(LocalDate day) {
        return day.getDayOfWeek().name().substring(0, 2);
    }

    /** « Place du marché, 11:30–14:00 », « Place du marché » sans horaires. */
    private static String spot(ResultSet resultSet) throws SQLException {
        String name = resultSet.getString("spot_name");
        String place = name == null || name.isBlank() ? "Emplacement annoncé" : name.trim();
        String start = resultSet.getString("start_time");
        String end = resultSet.getString("end_time");
        return start == null || end == null ? place : place + ", " + hhmm(start) + "–" + hhmm(end);
    }

    private static String hhmm(String time) {
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    private static List<String> kinds(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).sorted().toList();
    }
}
