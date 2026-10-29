package com.locallife.backend.activity.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Début de la prochaine occurrence de chaque activité, date affichée par les listes et la carte.
 *
 * <p>{@code activity.start_date} est le début de toute la programmation : une activité à plusieurs dates
 * séparées (ex. un vide-grenier le 19/04 et le 01/11) garde sa première date une fois celle-ci passée. La
 * prochaine occurrence suit la même règle que la recherche globale ({@code ActivitySearchRepository}) :
 * première occurrence non annulée dont le dernier jour, en heure locale de son lieu effectif, n'est pas
 * antérieur à la date de référence. Une occurrence en cours est donc retenue.
 */
@Repository
public class ActivityNextOccurrenceRepository {

    /** Fuseau d'une occurrence sans lieu ou sans fuseau connu : même règle que la recherche. */
    private static final String DEFAULT_TIMEZONE = "Europe/Paris";

    private static final String NEXT_START_SQL = """
            SELECT DISTINCT ON (s.activity_id) s.activity_id, (o.start_at AT TIME ZONE tz.zone) AS local_start
            FROM schedule s
            JOIN occurrence o ON o.schedule_id = s.id
            LEFT JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
            CROSS JOIN LATERAL (SELECT COALESCE(l.timezone, :defaultTimezone) AS zone) tz
            WHERE s.activity_id IN (:ids)
              AND o.status <> 'CANCELLED'
              AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE tz.zone)::date >= :from
            ORDER BY s.activity_id, o.start_at, o.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityNextOccurrenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Début local de la prochaine occurrence à partir de {@code from} (aujourd'hui, heure de Paris, si
     * {@code null}). Une activité sans occurrence à venir n'apparaît pas dans le résultat : l'appelant garde
     * alors {@code start_date}.
     */
    public Map<Long, LocalDateTime> findNextStartByActivityIds(Collection<Long> activityIds, LocalDate from) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (activityIds.isEmpty()) {
            return result;
        }
        LocalDate reference = from != null ? from : LocalDate.now(ZoneId.of(DEFAULT_TIMEZONE));
        jdbc.query(NEXT_START_SQL,
                new MapSqlParameterSource("ids", activityIds)
                        .addValue("from", reference)
                        .addValue("defaultTimezone", DEFAULT_TIMEZONE),
                row -> {
                    result.put(row.getLong("activity_id"), row.getObject("local_start", LocalDateTime.class));
                });
        return result;
    }
}
