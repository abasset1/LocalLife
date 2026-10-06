package com.locallife.backend.activityclassification.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Trace de classification d'un élément de source (CAT-008) — voir
 * {@code V38__create_classification_record.sql}. {@code activityId} est
 * renseigné si et seulement si {@code status.hasActivity()}.
 *
 * @param sourceTags             tags de la source, séparés par des virgules (relecture et mesure de leur apport)
 * @param candidateCategoryCodes codes V1 plausibles à départager, séparés par des virgules ({@code null} si aucun)
 */
public record ClassificationRecord(
        @Id Long id,
        Long sourceId,
        String importKey,
        Long activityId,
        ClassificationStatus status,
        String ruleId,
        String reason,
        String candidateCategoryCodes,
        String sourceCategory,
        String sourceTags,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
