package com.locallife.backend.activitytype.domain;

import org.springframework.data.annotation.Id;

/**
 * Traduction d'un {@link ActivityType} pour une locale donnée (CAT-002/
 * CAT-005, SPRINT_CATEG). Même rôle que
 * {@link com.locallife.backend.category.domain.CategoryTranslation} pour
 * les catégories : le label/la description ne sont jamais stockés sur
 * {@link ActivityType} elle-même, pour permettre d'ajouter des locales
 * sans modification de schéma (V1 : {@code fr-FR} uniquement).
 */
public record ActivityTypeTranslation(
        @Id Long id,
        String locale,
        String label,
        String description) {
}
