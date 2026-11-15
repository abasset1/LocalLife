package com.locallife.backend.media.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Image d'une activité ajoutée par son contributeur ({@code activity_photo}, V92). */
@Repository
public class ActivityPhotoRepository {

    /** Image stockée et date de sa dernière modification (version de son adresse). */
    public record Photo(String contentType, byte[] data, OffsetDateTime updatedAt) {

        /** Adresse de l'image, versionnée par sa date de modification (le contenu d'une adresse ne change pas). */
        public String url(Long activityId) {
            return "/api/v1/activities/" + activityId + "/photo?v=" + updatedAt.toInstant().toEpochMilli();
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityPhotoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Photo> find(Long activityId) {
        return jdbc.query("SELECT content_type, data, updated_at FROM activity_photo WHERE activity_id = :id",
                new MapSqlParameterSource("id", activityId),
                (row, index) -> new Photo(row.getString("content_type"), row.getBytes("data"),
                        row.getObject("updated_at", OffsetDateTime.class)))
                .stream().findFirst();
    }

    /** Ajoute ou remplace l'image de l'activité. */
    public void save(Long activityId, String contentType, byte[] data) {
        jdbc.update("""
                INSERT INTO activity_photo (activity_id, content_type, data, updated_at)
                VALUES (:id, :contentType, :data, now())
                ON CONFLICT (activity_id) DO UPDATE
                    SET content_type = EXCLUDED.content_type, data = EXCLUDED.data, updated_at = now()
                """,
                new MapSqlParameterSource("id", activityId).addValue("contentType", contentType)
                        .addValue("data", data));
    }

    /**
     * Image changée par son contributeur : une activité publiée ou refusée repart en validation, comme après une
     * modification du formulaire ({@code ContributionService#statusAfterUpdate}).
     */
    public void requireRevalidation(Long activityId) {
        jdbc.update("UPDATE activity SET status = 'PENDING' WHERE id = :id AND status IN ('PUBLISHED', 'REJECTED')",
                new MapSqlParameterSource("id", activityId));
    }

    /** @return {@code true} si une image a été retirée */
    public boolean delete(Long activityId) {
        return jdbc.update("DELETE FROM activity_photo WHERE activity_id = :id",
                new MapSqlParameterSource("id", activityId)) > 0;
    }
}
