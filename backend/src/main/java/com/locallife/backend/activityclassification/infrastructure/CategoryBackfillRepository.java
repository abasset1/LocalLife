package com.locallife.backend.activityclassification.infrastructure;

import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectures et écritures du rattrapage d'une catégorie sur les activités existantes
 * ({@code CategoryBackfillService}). N'ajoute que des liens {@code activity_category} : n'en retire jamais.
 */
@Repository
public class CategoryBackfillRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CategoryBackfillRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Activité candidate : titre et signaux d'import (catégorie d'origine, tags) tels que consignés dans
     * {@code classification_record}, {@code null}/vide pour une activité saisie à la main.
     */
    public record Candidate(Long activityId, String title, String sourceCategory, List<String> sourceTags) {
    }

    /**
     * Activités non archivées, pas encore terminées (ou sans date), qui ne portent pas déjà la catégorie.
     */
    public List<Candidate> findCandidates(Long categoryId) {
        return jdbc.query("""
                SELECT a.id, a.title, cr.source_category, cr.source_tags
                FROM activity a
                LEFT JOIN classification_record cr ON cr.activity_id = a.id
                WHERE a.status <> 'ARCHIVED'
                  AND (COALESCE(a.end_date, a.start_date) IS NULL OR COALESCE(a.end_date, a.start_date) >= now())
                  AND NOT EXISTS (SELECT 1 FROM activity_category ac
                                  WHERE ac.activity_id = a.id AND ac.category_id = :categoryId)
                ORDER BY a.id
                """, new MapSqlParameterSource("categoryId", categoryId), (rs, i) -> new Candidate(
                rs.getLong("id"), rs.getString("title"), rs.getString("source_category"),
                splitTags(rs.getString("source_tags"))));
    }

    /** Ajoute la catégorie à ces activités ; un lien déjà présent est ignoré. Rend le nombre de liens créés. */
    public int addCategory(Long categoryId, List<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource[] batch = activityIds.stream()
                .map(id -> new MapSqlParameterSource("activityId", id).addValue("categoryId", categoryId))
                .toArray(MapSqlParameterSource[]::new);
        int[] counts = jdbc.batchUpdate("""
                INSERT INTO activity_category (activity_id, category_id) VALUES (:activityId, :categoryId)
                ON CONFLICT (activity_id, category_id) DO NOTHING
                """, batch);
        return Arrays.stream(counts).map(count -> Math.max(count, 0)).sum();
    }

    /** {@code classification_record.source_tags} : tags joints par des virgules ({@code ClassificationService}). */
    static List<String> splitTags(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(tag -> !tag.isEmpty()).toList();
    }
}
