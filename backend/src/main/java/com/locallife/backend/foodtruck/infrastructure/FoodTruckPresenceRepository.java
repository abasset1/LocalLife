package com.locallife.backend.foodtruck.infrastructure;

import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.ManagedPresence;
import com.locallife.backend.foodtruck.domain.PresenceStatus;
import com.locallife.backend.foodtruck.domain.SpotKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
                        WHEN linked.place_status = 'PUBLISHED' THEN linked.id END AS place_id,
                   p.status, p.location_id
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

    // ---------------------------------------------------------------- gestion par le propriétaire (FOODTRUCK-01)

    /** Créneau et emplacement saisis par le propriétaire. */
    public record PresenceFields(
            Long locationId, SpotKind spotKind, String daysOfWeek, LocalTime startTime, LocalTime endTime,
            LocalDate validFrom, LocalDate validUntil) {
    }

    /** Toutes les présences d'un food truck, quel que soit leur statut. */
    public List<ManagedPresence> findAllForTruck(Long foodTruckId) {
        return jdbc.query(SELECT + """
                WHERE p.food_truck_id = :foodTruckId
                ORDER BY CASE p.status WHEN 'ARCHIVED' THEN 1 ELSE 0 END, p.valid_from NULLS FIRST,
                         p.start_time NULLS FIRST, p.id
                """, new MapSqlParameterSource("foodTruckId", foodTruckId), (row, index) -> toManaged(row));
    }

    public Optional<ManagedPresence> findManaged(Long id) {
        return jdbc.query(SELECT + "WHERE p.id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> toManaged(row)).stream().findFirst();
    }

    public Long insert(Long foodTruckId, PresenceFields fields, PresenceStatus status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, days_of_week, start_time,
                                                 end_time, valid_from, valid_until, status)
                VALUES (:foodTruckId, :locationId, :kind, :days, :start, :end, :from, :until, :status)
                """, fieldParams(fields).addValue("foodTruckId", foodTruckId).addValue("status", status.name()),
                key, new String[] {"id"});
        return key.getKey().longValue();
    }

    public void update(Long id, PresenceFields fields) {
        jdbc.update("""
                UPDATE food_truck_presence SET location_id = :locationId, spot_kind = :kind, days_of_week = :days,
                       start_time = :start, end_time = :end, valid_from = :from, valid_until = :until,
                       updated_at = now()
                WHERE id = :id
                """, fieldParams(fields).addValue("id", id));
    }

    /** Change le statut s'il est toujours l'un de {@code from} ; {@code false} sinon. */
    public boolean transition(Long id, Collection<PresenceStatus> from, PresenceStatus to) {
        return jdbc.update("""
                UPDATE food_truck_presence SET status = :to, updated_at = now()
                WHERE id = :id AND status IN (:from)
                """, new MapSqlParameterSource("id", id).addValue("to", to.name())
                .addValue("from", from.stream().map(Enum::name).toList())) == 1;
    }

    /** Emplacement non typé saisi par le propriétaire (adresse et position) ; renvoie son id. */
    public Long insertSpot(String name, String address, String postalCode, String city, double latitude,
            double longitude) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO location (name, address, postal_code, city, latitude, longitude, country_code, timezone,
                                      coordinate_precision)
                VALUES (:name, :address, :postalCode, :city, :latitude, :longitude, 'FR', 'Europe/Paris', 'EXACT')
                """, new MapSqlParameterSource("name", name).addValue("address", address)
                .addValue("postalCode", postalCode).addValue("city", city).addValue("latitude", latitude)
                .addValue("longitude", longitude), key, new String[] {"id"});
        return key.getKey().longValue();
    }

    /** Lieu LocalLife publié, utilisable comme emplacement existant. */
    public boolean isPublishedPlace(Long locationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM location WHERE id = :id AND place_status = 'PUBLISHED')",
                new MapSqlParameterSource("id", locationId), Boolean.class));
    }

    private static MapSqlParameterSource fieldParams(PresenceFields fields) {
        return new MapSqlParameterSource("locationId", fields.locationId()).addValue("kind", fields.spotKind().name())
                .addValue("days", fields.daysOfWeek()).addValue("start", fields.startTime())
                .addValue("end", fields.endTime()).addValue("from", fields.validFrom())
                .addValue("until", fields.validUntil());
    }

    private static ManagedPresence toManaged(ResultSet row) throws SQLException {
        return new ManagedPresence(toPresence(row), PresenceStatus.valueOf(row.getString("status")),
                row.getLong("location_id"));
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
