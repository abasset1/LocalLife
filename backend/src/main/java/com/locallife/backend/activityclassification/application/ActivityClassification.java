package com.locallife.backend.activityclassification.application;

import java.util.List;

/**
 * Vue agrégée de la classification d'une activité (CAT-006, SPRINT_CATEG ;
 * catégories simplifiées par l'issue #45) : {@code categories[]} (1..N, sans
 * notion de principale ou secondaire), {@code activityTypes[]},
 * {@code audiences[]}, {@code organizerType}.
 * Toutes les références portent un {@code categoryId}/{@code activityTypeId}/
 * {@code audienceId}/{@code organizerTypeId} (jamais un code ou un label) :
 * la résolution vers {@code CODE}/{@code LABEL} revient à l'appelant via
 * les services des référentiels (CAT-001 à CAT-005), pas à ce module, qui
 * ne gère que les relations.
 *
 * @param categoryIds     jamais {@code null} ; au moins une catégorie pour une
 *                        activité existante (contrainte en base, V42), dans
 *                        l'ordre d'enregistrement
 * @param activityTypeIds jamais {@code null}, éventuellement vide
 * @param audienceIds     jamais {@code null}, éventuellement vide
 * @param organizerTypeId {@code null} si non renseigné (0..1 par nature)
 */
public record ActivityClassification(
        List<Long> categoryIds,
        List<Long> activityTypeIds,
        List<Long> audienceIds,
        Long organizerTypeId) {
}
