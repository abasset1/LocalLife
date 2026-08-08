package com.locallife.backend.category.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Category. Aucune relation avec d'autres entités.
 */
public record Category(
        @Id Long id,
        String name,
        String description) {
}
