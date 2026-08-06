package com.locallife.backend.activity.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Activity. Aucune relation avec d'autres entités.
 */
public record Activity(
        @Id Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status) {
}
