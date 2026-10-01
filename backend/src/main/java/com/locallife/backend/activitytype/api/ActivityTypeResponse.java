package com.locallife.backend.activitytype.api;

/**
 * Représentation exposée par l'API pour un type d'activité (CAT-002).
 * Mêmes principes que {@code CategoryResponse} (CAT-001) : porte le
 * {@code code} métier, jamais l'{@code id} technique, avec un
 * {@code label}/{@code description} déjà résolus pour la locale demandée.
 */
public record ActivityTypeResponse(
        String code,
        String label,
        String description,
        boolean active) {
}
