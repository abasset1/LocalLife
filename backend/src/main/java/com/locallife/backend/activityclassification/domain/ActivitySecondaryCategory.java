package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Catégorie secondaire d'une activité (CAT-006, SPRINT_CATEG) : relation
 * 1 activité → 0..N catégories secondaires. Une ligne par catégorie
 * secondaire (même patron que {@code ActivityTag}, LL-11009), unicité
 * garantie par {@code UNIQUE(activity_id, category_id)} — voir
 * {@code V36__create_activity_classification_tables.sql}.
 */
public record ActivitySecondaryCategory(
        @Id Long id,
        Long activityId,
        Long categoryId) {
}
