package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.application.SourceSyncState;
import com.locallife.backend.collector.application.SyncStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Accès à {@code source_sync_state} (V68) : dernier essai d'import par source. */
@Repository
public class SourceSyncStateRepository {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final JdbcTemplate jdbcTemplate;

    public SourceSyncStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Remplace le dernier essai de la source ; sans effet si la source a été supprimée entre-temps. */
    public void record(
            Long sourceId, LocalDateTime attemptAt, SyncStatus status, String message, int pagesRead, int received,
            long durationMs) {
        String truncated = message == null || message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
        jdbcTemplate.update("""
                INSERT INTO source_sync_state
                    (source_id, last_attempt_at, last_status, last_message, last_pages_read, last_received,
                     last_duration_ms)
                SELECT id, ?, ?, ?, ?, ?, ? FROM source WHERE id = ?
                ON CONFLICT (source_id) DO UPDATE SET
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    last_status = EXCLUDED.last_status,
                    last_message = EXCLUDED.last_message,
                    last_pages_read = EXCLUDED.last_pages_read,
                    last_received = EXCLUDED.last_received,
                    last_duration_ms = EXCLUDED.last_duration_ms
                """, Timestamp.valueOf(attemptAt), status.name(), truncated, pagesRead, received, durationMs,
                sourceId);
    }

    /** États de toutes les sources collectables (API, DATATOURISME), y compris celles jamais importées. */
    public List<SourceSyncState> findAll() {
        return jdbcTemplate.query("""
                SELECT s.id, s.name, s.status, s.last_sync_at, st.last_attempt_at, st.last_status,
                       st.last_message, st.last_pages_read, st.last_received, st.last_duration_ms
                FROM source s
                LEFT JOIN source_sync_state st ON st.source_id = s.id
                WHERE s.type IN ('API', 'DATATOURISME')
                ORDER BY s.name, s.id
                """, (resultSet, rowNumber) -> {
                    Timestamp attempt = resultSet.getTimestamp("last_attempt_at");
                    Timestamp success = resultSet.getTimestamp("last_sync_at");
                    String status = resultSet.getString("last_status");
                    return new SourceSyncState(
                            resultSet.getLong("id"),
                            resultSet.getString("name"),
                            resultSet.getString("status"),
                            attempt == null ? null : attempt.toLocalDateTime(),
                            status == null ? null : SyncStatus.valueOf(status),
                            resultSet.getString("last_message"),
                            resultSet.getInt("last_pages_read"),
                            resultSet.getInt("last_received"),
                            resultSet.getLong("last_duration_ms"),
                            success == null ? null : success.toLocalDateTime());
                });
    }
}
