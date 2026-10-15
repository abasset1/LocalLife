package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.PlaceMatcher.Candidate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Paires candidates de doublons entre lieux d'événement (ADR-0011, § 7.2.13) — LECTURE SEULE. Deux lieux
 * d'événement utilisés par au moins une programmation, à 100 m au plus l'un de l'autre. Le classement est fait
 * par {@code EventLocationDuplicateMatcher}.
 */
@Repository
public class EventLocationDuplicateRepository {

    /** Lieu d'événement : ce qui est comparé, la commune, le nombre d'activités qui l'utilisent, son lien V63. */
    public record EventLocation(Long id, Candidate candidate, String city, int activityCount, Long linkedPlaceId) {
    }

    public record CandidatePair(EventLocation first, EventLocation second) {
    }

    private static final String PAIRS_SQL = """
            WITH used AS (
                SELECT location_id, count(DISTINCT activity_id) AS activities
                FROM (
                    SELECT s.location_id, s.activity_id FROM schedule s WHERE s.location_id IS NOT NULL
                    UNION ALL
                    SELECT o.location_id, s.activity_id FROM occurrence o JOIN schedule s ON s.id = o.schedule_id
                    WHERE o.location_id IS NOT NULL
                ) links
                GROUP BY location_id
            ),
            events AS (
                SELECT l.*, u.activities FROM location l JOIN used u ON u.location_id = l.id
                WHERE l.place_status IS NULL AND l.geo_location IS NOT NULL
                  AND (CAST(:department AS text) IS NULL OR l.postal_code LIKE :department || '%')
            )
            SELECT a.id AS a_id, a.name AS a_name, a.address AS a_address, a.postal_code AS a_postal,
                   a.city AS a_city, a.latitude AS a_lat, a.longitude AS a_lon,
                   a.coordinate_precision AS a_precision, a.activities AS a_activities, a.place_id AS a_linked,
                   b.id AS b_id, b.name AS b_name, b.address AS b_address, b.postal_code AS b_postal,
                   b.city AS b_city, b.latitude AS b_lat, b.longitude AS b_lon,
                   b.coordinate_precision AS b_precision, b.activities AS b_activities, b.place_id AS b_linked
            FROM events a
            JOIN events b ON a.id < b.id AND ST_DWithin(a.geo_location, b.geo_location, :maxDistance)
            ORDER BY a.id, b.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public EventLocationDuplicateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CandidatePair> findCandidatePairs(String department, double maxDistanceMeters) {
        List<CandidatePair> pairs = new ArrayList<>();
        jdbc.query(PAIRS_SQL, new MapSqlParameterSource("department", department)
                .addValue("maxDistance", maxDistanceMeters),
                row -> {
                    pairs.add(new CandidatePair(eventLocation(row, "a_"), eventLocation(row, "b_")));
                });
        return pairs;
    }

    private static EventLocation eventLocation(ResultSet row, String prefix) throws SQLException {
        return new EventLocation(
                row.getLong(prefix + "id"),
                new Candidate(row.getString(prefix + "name"), row.getString(prefix + "address"),
                        row.getString(prefix + "postal"), row.getObject(prefix + "lat", Double.class),
                        row.getObject(prefix + "lon", Double.class),
                        CoordinatePrecision.valueOf(row.getString(prefix + "precision"))),
                row.getString(prefix + "city"), row.getInt(prefix + "activities"),
                row.getObject(prefix + "linked", Long.class));
    }
}
