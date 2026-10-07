package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Catégorie d'une activité (issue #45) : relation 1 activité → 1..N catégories, sans notion de
 * catégorie principale ou secondaire. Une ligne par catégorie (même patron que {@code ActivityTag},
 * LL-11009), unicité garantie par {@code UNIQUE(activity_id, category_id)} et « au moins une
 * catégorie » par un trigger de contrainte différé — voir {@code V42__activity_category_n_categories.sql}.
 */
public record ActivityCategory(
        @Id Long id,
        Long activityId,
        Long categoryId) {
}
