package com.locallife.backend.audience.domain;

import org.springframework.data.annotation.Id;

/**
 * Traduction d'un {@link Audience} pour une locale donnée (CAT-003/CAT-005,
 * SPRINT_CATEG). Même rôle que
 * {@link com.locallife.backend.category.domain.CategoryTranslation} et
 * {@link com.locallife.backend.activitytype.domain.ActivityTypeTranslation} :
 * le label/la description ne sont jamais stockés sur {@link Audience}
 * elle-même (V1 : {@code fr-FR} uniquement).
 */
public record AudienceTranslation(
        @Id Long id,
        String locale,
        String label,
        String description) {
}
