package com.locallife.backend.collector.application;

import java.time.LocalDateTime;

/**
 * Dernier essai d'import d'une source (table {@code source_sync_state}, V68), distinct de
 * {@code Source.lastSyncAt} qui ne retient que le dernier import <b>complet</b> réussi.
 *
 * @param lastSuccessAt dernier import complet ({@code source.last_sync_at}), {@code null} s'il n'y en a jamais eu
 */
public record SourceSyncState(
        Long sourceId,
        String sourceName,
        String sourceStatus,
        LocalDateTime lastAttemptAt,
        SyncStatus lastStatus,
        String lastMessage,
        int lastPagesRead,
        int lastReceived,
        long lastDurationMs,
        LocalDateTime lastSuccessAt) {
}
