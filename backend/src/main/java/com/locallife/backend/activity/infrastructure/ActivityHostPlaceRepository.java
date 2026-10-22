package com.locallife.backend.activity.infrastructure;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lieu qui propose une activité ({@code activity_host_place}, V71, décision du 22/10/2026) : choisi par l'auteur
 * parmi les lieux publiés qu'il gère. Affiché seulement tant que l'auteur gère encore ce lieu.
 */
@Repository
public class ActivityHostPlaceRepository {

    /** Lieu affiché « proposé par ». */
    public record HostPlace(Long id, String name) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityHostPlaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lieu choisi, qu'il soit encore affichable ou non (pré-remplissage du formulaire). */
    public Optional<Long> findChosen(Long activityId) {
        return jdbc.queryForList("SELECT location_id FROM activity_host_place WHERE activity_id = :activityId",
                new MapSqlParameterSource("activityId", activityId), Long.class).stream().findFirst();
    }

    /** Lieu affiché : publié, et toujours géré par l'auteur de l'activité. */
    public Optional<HostPlace> findDisplayed(Long activityId) {
        return jdbc.query("""
                SELECT l.id, l.name FROM activity_host_place h
                JOIN activity a ON a.id = h.activity_id
                JOIN location l ON l.id = h.location_id AND l.place_status = 'PUBLISHED'
                WHERE h.activity_id = :activityId
                  AND EXISTS (SELECT 1 FROM place_manager m
                              WHERE m.location_id = h.location_id AND m.user_id = a.created_by
                                AND m.revoked_at IS NULL)
                """, new MapSqlParameterSource("activityId", activityId),
                (row, index) -> new HostPlace(row.getLong("id"), row.getString("name"))).stream().findFirst();
    }

    /** Ce compte peut-il rattacher une activité à ce lieu : lieu publié qu'il gère ? */
    public boolean canHost(Long userId, Long locationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM place_manager m JOIN location l ON l.id = m.location_id
                               WHERE m.location_id = :locationId AND m.user_id = :userId AND m.revoked_at IS NULL
                                 AND l.place_status = 'PUBLISHED')
                """, new MapSqlParameterSource("locationId", locationId).addValue("userId", userId), Boolean.class));
    }

    /** Pose ou retire ({@code locationId} nul) le lieu d'une activité. */
    public void set(Long activityId, Long locationId) {
        MapSqlParameterSource params = new MapSqlParameterSource("activityId", activityId)
                .addValue("locationId", locationId);
        if (locationId == null) {
            jdbc.update("DELETE FROM activity_host_place WHERE activity_id = :activityId", params);
            return;
        }
        jdbc.update("""
                INSERT INTO activity_host_place (activity_id, location_id) VALUES (:activityId, :locationId)
                ON CONFLICT (activity_id) DO UPDATE SET location_id = EXCLUDED.location_id
                """, params);
    }
}
