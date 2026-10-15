package com.locallife.backend.activity.infrastructure;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Activités à venir d'un lieu permanent publié (ADR-0011, § 2.4 et § 7.2.10) : celles qui se déroulent au lieu
 * lui-même ou à un lieu d'événement relié par un administrateur ({@code location.place_id}, V63). Lieu effectif :
 * lieu d'une programmation ou d'une occurrence non annulée, comme ADR-0006.
 */
@Repository
public class ActivityAtPlaceRepository {

    private static final String PUBLISHED_PLACE_SQL =
            "SELECT count(*) FROM location WHERE id = :placeId AND place_status = 'PUBLISHED'";

    private static final String UPCOMING_SQL = """
            WITH locs AS (
                SELECT id FROM location WHERE id = :placeId OR place_id = :placeId
            ),
            hits AS (
                SELECT s.activity_id FROM schedule s WHERE s.location_id IN (SELECT id FROM locs)
                UNION
                SELECT s.activity_id FROM occurrence o JOIN schedule s ON s.id = o.schedule_id
                WHERE o.location_id IN (SELECT id FROM locs) AND o.status <> 'CANCELLED'
            )
            SELECT a.id
            FROM activity a JOIN hits h ON h.activity_id = a.id
            WHERE a.status = 'PUBLISHED'
              AND (COALESCE(a.end_date, a.start_date) IS NULL
                   OR COALESCE(a.end_date, a.start_date) >= (now() AT TIME ZONE 'Europe/Paris')::date)
            ORDER BY a.start_date NULLS LAST, a.id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityAtPlaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isPublishedPlace(Long placeId) {
        Long count = jdbc.queryForObject(PUBLISHED_PLACE_SQL, new MapSqlParameterSource("placeId", placeId),
                Long.class);
        return count != null && count > 0;
    }

    /** Identifiants des activités publiées et non terminées, de la plus proche à la plus lointaine. */
    public List<Long> findUpcomingActivityIds(Long placeId, int limit) {
        return jdbc.queryForList(UPCOMING_SQL, new MapSqlParameterSource("placeId", placeId).addValue("limit", limit),
                Long.class);
    }
}
