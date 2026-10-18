package com.locallife.backend.foodtruck.infrastructure;

import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.SpotKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Présences des food trucks (ADR-0013, table {@code food_truck_presence}). La position affichée d'un food truck est
 * celle de l'emplacement de sa présence ; un food truck n'est jamais un lieu.
 */
@Repository
public class FoodTruckPresenceRepository {

    private static final String SELECT = """
            SELECT p.id, p.food_truck_id, ft.name, ft.description, ft.category, ft.contact,
                   l.latitude, l.longitude, COALESCE(l.name, l.address, l.city) AS spot_name, p.spot_kind,
                   p.days_of_week, p.start_time, p.end_time, p.valid_from, p.valid_until,
                   CASE WHEN l.place_status = 'PUBLISHED' THEN l.id
                        WHEN linked.place_status = 'PUBLISHED' THEN linked.id END AS place_id
            FROM food_truck_presence p
            JOIN food_truck ft ON ft.id = p.food_truck_id
            JOIN location l ON l.id = p.location_id
            LEFT JOIN location linked ON linked.id = l.place_id
            """;

    /** Présence publiée d'un food truck publié, valable le jour {@code :day} (code {@code :dayCode}). */
    private static final String ACTIVE_ON = """
            p.status = 'PUBLISHED' AND ft.status = 'PUBLISHED'
              AND (p.valid_from IS NULL OR p.valid_from <= :day)
              AND (p.valid_until IS NULL OR p.valid_until >= :day)
              AND (p.days_of_week IS NULL OR position(:dayCode IN p.days_of_week) > 0)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FoodTruckPresenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Présences actives le jour donné ; limitées à un lieu LocalLife si {@code placeId} est fourni. */
    public List<FoodTruckPresence> findActiveOn(LocalDate day, Long placeId) {
        String placeFilter = placeId == null ? "" : "AND (l.id = :placeId OR l.place_id = :placeId) ";
        return jdbc.query(SELECT + "WHERE " + ACTIVE_ON + placeFilter
                        + "ORDER BY ft.name, p.start_time NULLS FIRST, p.id",
                dayParams(day).addValue("placeId", placeId), (row, index) -> toPresence(row));
    }

    /** Présences publiées d'un food truck encore valables à partir de {@code from} : son planning. */
    public List<FoodTruckPresence> findUpcomingForTruck(Long foodTruckId, LocalDate from) {
        return jdbc.query(SELECT + """
                WHERE p.food_truck_id = :foodTruckId AND p.status = 'PUBLISHED'
                  AND (p.valid_until IS NULL OR p.valid_until >= :day)
                ORDER BY p.valid_from NULLS FIRST, p.start_time NULLS FIRST, p.id
                """, new MapSqlParameterSource("foodTruckId", foodTruckId).addValue("day", from),
                (row, index) -> toPresence(row));
    }

    /**
     * Présence permanente publiée (sans jours ni dates) sur un emplacement non typé créé à la position donnée —
     * forme des food trucks créés avant leur gestion par un professionnel (ADR-0013, décision D3). Renvoie son id.
     */
    public Long insertPermanent(Long foodTruckId, double latitude, double longitude) {
        KeyHolder location = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO location (latitude, longitude, country_code, timezone, coordinate_precision)
                VALUES (:latitude, :longitude, 'FR', 'Europe/Paris', 'UNKNOWN')
                """, new MapSqlParameterSource("latitude", latitude).addValue("longitude", longitude), location,
                new String[] {"id"});
        KeyHolder presence = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, status)
                VALUES (:foodTruckId, :locationId, :kind, 'PUBLISHED')
                """, new MapSqlParameterSource("foodTruckId", foodTruckId)
                .addValue("locationId", location.getKey().longValue()).addValue("kind", SpotKind.OTHER.name()),
                presence, new String[] {"id"});
        return presence.getKey().longValue();
    }

    private static MapSqlParameterSource dayParams(LocalDate day) {
        return new MapSqlParameterSource("day", day).addValue("dayCode", dayCode(day.getDayOfWeek()));
    }

    /** Code du jour dans {@code days_of_week} : {@code MO}, {@code TU}… */
    static String dayCode(DayOfWeek day) {
        return day.name().substring(0, 2);
    }

    private static FoodTruckPresence toPresence(ResultSet row) throws SQLException {
        return new FoodTruckPresence(
                row.getLong("id"), row.getLong("food_truck_id"), row.getString("name"), row.getString("description"),
                row.getString("category"), row.getString("contact"), row.getObject("latitude", Double.class),
                row.getObject("longitude", Double.class), row.getString("spot_name"),
                SpotKind.valueOf(row.getString("spot_kind")), row.getString("days_of_week"),
                row.getObject("start_time", LocalTime.class), row.getObject("end_time", LocalTime.class),
                row.getObject("valid_from", LocalDate.class), row.getObject("valid_until", LocalDate.class),
                row.getObject("place_id", Long.class));
    }
}
