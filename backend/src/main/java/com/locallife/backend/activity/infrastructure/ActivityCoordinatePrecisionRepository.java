package com.locallife.backend.activity.infrastructure;

import com.locallife.backend.place.domain.CoordinatePrecision;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Précision géographique copiée sur l'activité ({@code activity.coordinate_precision}, ADR-0011, phase A).
 *
 * <p>La colonne n'est volontairement pas un champ de {@code Activity} : l'activité est recopiée à de nombreux
 * endroits (changement de statut, mise à jour d'import…) et un champ avec valeur par défaut y serait remis à
 * {@code UNKNOWN} à chaque copie. Spring Data JDBC n'écrivant que les colonnes de l'entité, la précision n'est
 * modifiée que par {@link #update}, appelée par l'import après le rattachement du lieu.
 */
@Repository
public class ActivityCoordinatePrecisionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityCoordinatePrecisionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void update(Long activityId, CoordinatePrecision precision) {
        jdbc.update("UPDATE activity SET coordinate_precision = :precision WHERE id = :id",
                new MapSqlParameterSource("id", activityId).addValue("precision", precision.name()));
    }

    /** Précision des activités demandées ; une activité absente de la base n'apparaît pas dans le résultat. */
    public Map<Long, CoordinatePrecision> findByActivityIds(Collection<Long> activityIds) {
        Map<Long, CoordinatePrecision> result = new HashMap<>();
        if (activityIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT id, coordinate_precision FROM activity WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", activityIds),
                row -> {
                    result.put(row.getLong("id"), CoordinatePrecision.valueOf(row.getString("coordinate_precision")));
                });
        return result;
    }

    public CoordinatePrecision findByActivityId(Long activityId) {
        return findByActivityIds(List.of(activityId)).getOrDefault(activityId, CoordinatePrecision.UNKNOWN);
    }
}
