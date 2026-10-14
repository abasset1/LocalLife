package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.PlaceMatcher.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Paires candidates au rapprochement d'un lieu d'événement et d'un lieu permanent (ADR-0011, § 2.3), classées par
 * {@code PlaceMatcher}, et lien réversible {@code location.place_id} (V63, § 7.2.9) posé par un administrateur.
 */
@Repository
public class PlaceMatchRepository {

    /**
     * Paire candidate : un lieu d'événement utilisé par au moins une activité, un lieu permanent proche.
     * {@code linkedPlaceId} : lieu permanent auquel le lieu d'événement est déjà relié (V63), ou {@code null}.
     */
    public record CandidatePair(
            Long eventLocationId, Candidate eventLocation, String eventCity, int activityCount,
            Long placeId, Candidate place, String placeType, String placeStatus, Long linkedPlaceId) {
    }

    private static final String CANDIDATES_SQL = """
            WITH used AS (
                SELECT location_id, count(DISTINCT activity_id) AS activities
                FROM (
                    SELECT s.location_id, s.activity_id FROM schedule s WHERE s.location_id IS NOT NULL
                    UNION ALL
                    SELECT o.location_id, s.activity_id FROM occurrence o JOIN schedule s ON s.id = o.schedule_id
                    WHERE o.location_id IS NOT NULL
                ) links
                GROUP BY location_id
            )
            SELECT e.id AS e_id, e.name AS e_name, e.address AS e_address, e.postal_code AS e_postal,
                   e.city AS e_city, e.latitude AS e_lat, e.longitude AS e_lon,
                   e.coordinate_precision AS e_precision, u.activities, e.place_id AS e_linked,
                   p.id AS p_id, p.name AS p_name, p.address AS p_address, p.postal_code AS p_postal,
                   p.latitude AS p_lat, p.longitude AS p_lon, p.coordinate_precision AS p_precision,
                   p.place_type, p.place_status
            FROM location e
            JOIN used u ON u.location_id = e.id
            JOIN location p ON p.place_status IN ('PENDING', 'PUBLISHED')
                           AND p.id <> e.id
                           AND p.geo_location IS NOT NULL
                           AND ST_DWithin(p.geo_location, e.geo_location, :maxDistance)
            WHERE e.place_status IS NULL
              AND e.geo_location IS NOT NULL
              AND (CAST(:department AS text) IS NULL OR e.postal_code LIKE :department || '%')
              AND (CAST(:eventLocationId AS bigint) IS NULL OR e.id = :eventLocationId)
              AND (CAST(:placeId AS bigint) IS NULL OR p.id = :placeId)
            ORDER BY e.id, p.id
            """;

    private static final String LINK_SQL = """
            UPDATE location SET place_id = :placeId, place_linked_by = :adminId, place_linked_at = now()
            WHERE id = :eventLocationId AND place_status IS NULL
            """;

    private static final String UNLINK_SQL = """
            UPDATE location SET place_id = NULL, place_linked_by = NULL, place_linked_at = NULL
            WHERE id = :eventLocationId AND place_id IS NOT NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PlaceMatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CandidatePair> findCandidatePairs(String department, double maxDistanceMeters) {
        return query(department, null, null, maxDistanceMeters);
    }

    /** La paire si elle est candidate (lieu d'événement utilisé, lieu permanent proche et en attente ou publié). */
    public Optional<CandidatePair> findCandidatePair(Long eventLocationId, Long placeId, double maxDistanceMeters) {
        return query(null, eventLocationId, placeId, maxDistanceMeters).stream().findFirst();
    }

    /** Relie un lieu d'événement à un lieu permanent ; {@code false} si ce n'est pas un lieu d'événement. */
    public boolean link(Long eventLocationId, Long placeId, Long adminId) {
        return jdbc.update(LINK_SQL, new MapSqlParameterSource("eventLocationId", eventLocationId)
                .addValue("placeId", placeId).addValue("adminId", adminId)) == 1;
    }

    /** Délie un lieu d'événement ; {@code false} s'il n'était pas relié. */
    public boolean unlink(Long eventLocationId) {
        return jdbc.update(UNLINK_SQL, new MapSqlParameterSource("eventLocationId", eventLocationId)) == 1;
    }

    private List<CandidatePair> query(String department, Long eventLocationId, Long placeId, double maxDistance) {
        List<CandidatePair> pairs = new ArrayList<>();
        jdbc.query(CANDIDATES_SQL, new MapSqlParameterSource("department", department)
                .addValue("eventLocationId", eventLocationId).addValue("placeId", placeId)
                .addValue("maxDistance", maxDistance), row -> {
                    pairs.add(new CandidatePair(
                            row.getLong("e_id"),
                            new Candidate(row.getString("e_name"), row.getString("e_address"),
                                    row.getString("e_postal"), row.getObject("e_lat", Double.class),
                                    row.getObject("e_lon", Double.class),
                                    CoordinatePrecision.valueOf(row.getString("e_precision"))),
                            row.getString("e_city"), row.getInt("activities"),
                            row.getLong("p_id"),
                            new Candidate(row.getString("p_name"), row.getString("p_address"),
                                    row.getString("p_postal"), row.getObject("p_lat", Double.class),
                                    row.getObject("p_lon", Double.class),
                                    CoordinatePrecision.valueOf(row.getString("p_precision"))),
                            row.getString("place_type"), row.getString("place_status"),
                            row.getObject("e_linked", Long.class)));
                });
        return pairs;
    }
}
