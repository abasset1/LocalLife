package com.locallife.backend.follow.infrastructure;

import com.locallife.backend.follow.domain.FollowRecipient;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.domain.FollowedItem;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux suivis ({@code follow}) et aux changements enregistrés par les triggers ({@code follow_change}, V79), en
 * {@link JdbcTemplate} comme les alertes.
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

    public boolean exists(Long userId, FollowTargetType type, Long targetId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM follow WHERE user_id = ? AND " + column(type) + " = ?)",
                Boolean.class, userId, targetId));
    }

    /** Idempotent : suivre deux fois la même cible ne crée qu'un suivi. */
    public void insert(Long userId, FollowTargetType type, Long targetId) {
        String column = column(type);
        jdbcTemplate.update("INSERT INTO follow (user_id, " + column + ", last_change_id) VALUES (?, ?, "
                + LAST_CHANGE + ") ON CONFLICT (user_id, " + column + ") WHERE " + column
                + " IS NOT NULL DO NOTHING", userId, targetId);
    }

    public void delete(Long userId, FollowTargetType type, Long targetId) {
        jdbcTemplate.update("DELETE FROM follow WHERE user_id = ? AND " + column(type) + " = ?", userId, targetId);
    }

    public List<FollowedItem> findByUserId(Long userId) {
        return jdbcTemplate.query(FOLLOWED_ITEMS, (resultSet, rowNumber) -> new FollowedItem(
                FollowTargetType.valueOf(resultSet.getString("type")), resultSet.getLong("target_id"),
                resultSet.getString("title"), resultSet.getString("city"), resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class)), userId, userId);
    }

    /** Utilisateurs qui suivent au moins une activité ou un lieu, avec leur adresse si elle est vérifiée. */
    public List<FollowRecipient> findRecipients() {
        return jdbcTemplate.query("""
                SELECT u.id, CASE WHEN u.email_verified THEN u.email END AS email
                FROM users u WHERE EXISTS (SELECT 1 FROM follow f WHERE f.user_id = u.id) ORDER BY u.id""",
                (resultSet, rowNumber) -> new FollowRecipient(resultSet.getLong("id"), resultSet.getString("email")));
    }

    /**
     * Mises à jour à notifier à un utilisateur : changements depuis le dernier envoi et, pour les lieux, au plus
     * {@code maxNewActivitiesPerPlace} nouvelles activités chacun. Suivis sans rien de nouveau : absents.
     */
    public List<FollowUpdate> findPendingUpdates(Long userId, int maxNewActivitiesPerPlace) {
        Map<Long, FollowUpdate> updates = new LinkedHashMap<>();
        jdbcTemplate.query(PENDING_CHANGES, (ResultSet resultSet) -> {
            long followId = resultSet.getLong("follow_id");
            updates.put(followId, new FollowUpdate(followId, FollowTargetType.valueOf(resultSet.getString("type")),
                    resultSet.getLong("target_id"), resultSet.getString("title"), kinds(resultSet.getArray("kinds")),
                    new ArrayList<>(), resultSet.getLong("max_change_id")));
        }, userId, userId);
        jdbcTemplate.query(NEW_ACTIVITIES_AT_PLACES, (ResultSet resultSet) -> {
            long followId = resultSet.getLong("follow_id");
            long placeId = resultSet.getLong("place_id");
            String placeName = resultSet.getString("place_name");
            FollowUpdate update = updates.computeIfAbsent(followId, id -> new FollowUpdate(
                    id, FollowTargetType.PLACE, placeId, placeName, List.of(), new ArrayList<>(), 0));
            update.newActivities().add(resultSet.getString("title"));
        }, maxNewActivitiesPerPlace, userId);
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

    /** Pour un compte sans droit au suivi : rien de ce qui précède {@code until} ne lui sera envoyé plus tard. */
    public void markAllNotified(Long userId, OffsetDateTime until) {
        jdbcTemplate.update("UPDATE follow SET last_change_id = " + LAST_CHANGE + ", notified_until = ? "
                + "WHERE user_id = ?", until, userId);
    }

    /** Changements de plus de {@code days} jours : déjà notifiés ou trop anciens pour l'être. */
    public int purgeChangesOlderThan(int days) {
        return jdbcTemplate.update("DELETE FROM follow_change WHERE changed_at < now() - make_interval(days => ?)",
                days);
    }

    private static String column(FollowTargetType type) {
        return type == FollowTargetType.ACTIVITY ? "activity_id" : "location_id";
    }

    private static List<String> kinds(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).sorted().toList();
    }
}
