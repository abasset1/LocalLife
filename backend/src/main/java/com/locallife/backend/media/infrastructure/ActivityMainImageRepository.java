package com.locallife.backend.media.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Image principale (premier média par position) de plusieurs activités en une seule requête : les listes de la
 * carte ({@code /within-bounds}, {@code /nearby}, favoris) résolvaient auparavant une à deux requêtes par
 * activité, soit des milliers d'allers-retours sur une vue large (visiteur sans compte, carte dézoomée).
 */
@Repository
public class ActivityMainImageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityMainImageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Url de l'image principale par activité ; une activité sans média n'apparaît pas dans le résultat. L'image
     * ajoutée par le contributeur ({@code activity_photo}, V92) passe avant les médias importés : son adresse est
     * un chemin de l'API, versionné par sa date de modification (même principe que la photo d'un food truck).
     */
    public Map<Long, String> findMainImageUrls(Collection<Long> activityIds) {
        Map<Long, String> result = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT DISTINCT ON (activity_id) activity_id, url
                FROM (
                    SELECT ap.activity_id, 0 AS priority, 0 AS position, 0 AS link_id,
                           '/api/v1/activities/' || ap.activity_id || '/photo?v='
                               || FLOOR(EXTRACT(EPOCH FROM ap.updated_at) * 1000)::BIGINT AS url
                    FROM activity_photo ap
                    WHERE ap.activity_id IN (:ids)
                    UNION ALL
                    SELECT am.activity_id, 1, am.position, am.id, m.url
                    FROM activity_media am
                    JOIN media m ON m.id = am.media_id
                    WHERE am.activity_id IN (:ids)
                ) images
                ORDER BY activity_id, priority, position, link_id
                """,
                new MapSqlParameterSource("ids", activityIds),
                row -> {
                    result.put(row.getLong("activity_id"), row.getString("url"));
                });
        return result;
    }
}
