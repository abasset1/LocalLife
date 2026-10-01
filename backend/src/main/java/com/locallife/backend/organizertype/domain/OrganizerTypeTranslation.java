package com.locallife.backend.organizertype.domain;

import org.springframework.data.annotation.Id;

/**
 * Traduction d'un {@link OrganizerType} pour une locale donnée
 * (CAT-004/CAT-005, SPRINT_CATEG). Même rôle que
 * {@code CategoryTranslation}/{@code ActivityTypeTranslation}/
 * {@code AudienceTranslation} : le label/la description ne sont jamais
 * stockés sur {@link OrganizerType} elle-même (V1 : {@code fr-FR}
 * uniquement).
 */
public record OrganizerTypeTranslation(
        @Id Long id,
        String locale,
        String label,
        String description) {
}
