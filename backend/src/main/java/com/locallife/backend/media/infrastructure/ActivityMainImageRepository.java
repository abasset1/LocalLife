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

    /** Url de l'image principale par activité ; une activité sans média n'apparaît pas dans le résultat. */
    public Map<Long, String> findMainImageUrls(Collection<Long> activityIds) {
        Map<Long, String> result = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT DISTINCT ON (am.activity_id) am.activity_id, m.url
                FROM activity_media am
                JOIN media m ON m.id = am.media_id
                WHERE am.activity_id IN (:ids)
                ORDER BY am.activity_id, am.position, am.id
                """,
                new MapSqlParameterSource("ids", activityIds),
                row -> {
                    result.put(row.getLong("activity_id"), row.getString("url"));
                });
        return result;
    }
}
