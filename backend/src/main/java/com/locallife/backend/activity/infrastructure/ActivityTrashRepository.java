package com.locallife.backend.activity.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Corbeille des activités et motifs de modération (V73, décision du 22/10/2026) : une suppression par un
 * administrateur passe l'activité au statut {@code DELETED} en gardant son statut d'avant ; elle est restaurable 30
 * jours, puis purgée. Colonnes hors de l'entité {@code Activity} : enregistrer une activité ne les touche jamais.
 */
@Repository
public class ActivityTrashRepository {

    /** Activité à la corbeille. */
    public record TrashedActivity(
            Long id, String title, String city, LocalDateTime startDate, String previousStatus, Long sourceId,
            Long createdBy, OffsetDateTime deletedAt, Long deletedBy, String deletedByEmail, String deletionReason,
            UUID deletionBatch) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityTrashRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Met à la corbeille ; renvoie les ids effectivement déplacés (ceux qui n'y étaient pas déjà). */
    public List<Long> trash(Collection<Long> ids, Long adminId, String reason, UUID batch) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                UPDATE activity SET previous_status = status, status = 'DELETED', deleted_at = now(),
                       deleted_by = :adminId, deletion_reason = :reason, deletion_batch = :batch
                WHERE id IN (:ids) AND status <> 'DELETED'
                RETURNING id
                """, new MapSqlParameterSource("ids", ids).addValue("adminId", adminId).addValue("reason", reason)
                .addValue("batch", batch), Long.class);
    }

    /** Sort de la corbeille avec le statut d'avant ; renvoie les ids restaurés. */
    public List<Long> restore(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                UPDATE activity SET status = previous_status, previous_status = NULL, deleted_at = NULL,
                       deleted_by = NULL, deletion_reason = NULL, deletion_batch = NULL
                WHERE id IN (:ids) AND status = 'DELETED'
                RETURNING id
                """, new MapSqlParameterSource("ids", ids), Long.class);
    }

    public List<Long> idsInBatch(UUID batch) {
        return jdbc.queryForList("SELECT id FROM activity WHERE deletion_batch = :batch AND status = 'DELETED'",
                new MapSqlParameterSource("batch", batch), Long.class);
    }

    public Optional<TrashedActivity> findTrashed(Long id) {
        return jdbc.query(SELECT + "WHERE a.id = :id AND a.status = 'DELETED'", new MapSqlParameterSource("id", id),
                (row, index) -> toTrashed(row)).stream().findFirst();
    }

    /** Corbeille, les suppressions les plus récentes d'abord. */
    public List<TrashedActivity> findTrash(int offset, int limit) {
        return jdbc.query(SELECT + "WHERE a.status = 'DELETED' ORDER BY a.deleted_at DESC, a.id DESC "
                + "LIMIT :limit OFFSET :offset", new MapSqlParameterSource("limit", limit).addValue("offset", offset),
                (row, index) -> toTrashed(row));
    }

    public long countTrash() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM activity WHERE status = 'DELETED'",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    /** Activités à la corbeille depuis avant {@code before}, à purger. */
    public List<Long> expired(OffsetDateTime before) {
        return jdbc.queryForList("SELECT id FROM activity WHERE status = 'DELETED' AND deleted_at < :before",
                new MapSqlParameterSource("before", before), Long.class);
    }

    /** Motif d'un refus ou d'une annulation par l'équipe ({@code null} pour l'effacer). */
    public void setModerationReason(Long id, String reason) {
        jdbc.update("UPDATE activity SET moderation_reason = :reason WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("reason", reason));
    }

    public Optional<String> moderationReason(Long id) {
        return jdbc.queryForList("SELECT moderation_reason FROM activity WHERE id = :id AND moderation_reason "
                + "IS NOT NULL", new MapSqlParameterSource("id", id), String.class).stream().findFirst();
    }

    private static final String SELECT = """
            SELECT a.id, a.title, a.city, a.start_date, a.previous_status, a.source_id, a.created_by, a.deleted_at,
                   a.deleted_by, u.email AS deleted_by_email, a.deletion_reason, a.deletion_batch
            FROM activity a LEFT JOIN users u ON u.id = a.deleted_by
            """;

    private static TrashedActivity toTrashed(java.sql.ResultSet row) throws java.sql.SQLException {
        return new TrashedActivity(row.getLong("id"), row.getString("title"), row.getString("city"),
                row.getObject("start_date", LocalDateTime.class), row.getString("previous_status"),
                row.getObject("source_id", Long.class), row.getObject("created_by", Long.class),
                row.getObject("deleted_at", OffsetDateTime.class), row.getObject("deleted_by", Long.class),
                row.getString("deleted_by_email"), row.getString("deletion_reason"),
                row.getObject("deletion_batch", UUID.class));
    }
}
