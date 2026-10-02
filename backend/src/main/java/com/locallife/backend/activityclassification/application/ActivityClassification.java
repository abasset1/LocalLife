package com.locallife.backend.activityclassification.application;

import java.util.List;

/**
 * Vue agrégée de la classification d'une activité (CAT-006, SPRINT_CATEG)
 * — reprend exactement les 5 dimensions du modèle cible décrit par le
 * ticket : {@code primaryCategory}, {@code secondaryCategories[]},
 * {@code activityTypes[]}, {@code audiences[]}, {@code organizerType}.
 * Toutes les références portent un {@code categoryId}/{@code activityTypeId}/
 * {@code audienceId}/{@code organizerTypeId} (jamais un code ou un label) :
 * la résolution vers {@code CODE}/{@code LABEL} revient à l'appelant via
 * les services des référentiels (CAT-001 à CAT-005), pas à ce module, qui
 * ne gère que les relations.
 *
 * @param primaryCategoryId   {@code null} si l'activité n'est pas encore
 *                            classifiée (voir {@code ActivityPrimaryCategory})
 * @param secondaryCategoryIds jamais {@code null}, éventuellement vide
 * @param activityTypeIds      jamais {@code null}, éventuellement vide
 * @param audienceIds           jamais {@code null}, éventuellement vide
 * @param organizerTypeId      {@code null} si non renseigné (0..1 par nature)
 */
public record ActivityClassification(
        Long primaryCategoryId,
        List<Long> secondaryCategoryIds,
        List<Long> activityTypeIds,
        List<Long> audienceIds,
        Long organizerTypeId) {
}
