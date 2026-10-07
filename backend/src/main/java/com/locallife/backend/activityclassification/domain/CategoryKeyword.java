package com.locallife.backend.activityclassification.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Mot-clé métier du fallback titre — voir {@code V39__create_category_keyword.sql}. Le mot-clé est
 * stocké tel quel (accents compris) ; il référence une catégorie V1 existante.
 */
public record CategoryKeyword(
        @Id Long id,
        Long categoryId,
        String keyword,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
