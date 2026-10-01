package com.locallife.backend.category.api;

/**
 * Représentation exposée par l'API pour une catégorie (CAT-001). Porte le
 * {@code code} métier (jamais l'{@code id} technique interne — règle
 * ID/CODE/LABEL du sprint, {@code docs/05_Sprints/SPRINT_CATEG.md}) et un
 * {@code label}/{@code description} déjà résolus pour la locale demandée,
 * pour ne pas exposer la structure interne de traduction
 * ({@link com.locallife.backend.category.domain.CategoryTranslation}) aux
 * consommateurs de l'API.
 */
public record CategoryResponse(
        String code,
        String label,
        String description,
        boolean active) {
}
