package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Catégorie principale d'une activité (CAT-006, SPRINT_CATEG) : relation
 * 1 activité → 0..1 catégorie en base (voir le commentaire de
 * {@code V36__create_activity_classification_tables.sql} pour pourquoi ce
 * n'est pas encore NOT NULL/1..1 au niveau base). « Obligatoire » est
 * appliqué par {@link com.locallife.backend.activityclassification.application.ActivityClassificationService},
 * pas encore par une contrainte SQL.
 */
public record ActivityPrimaryCategory(
        @Id Long id,
        Long activityId,
        Long categoryId) {
}
