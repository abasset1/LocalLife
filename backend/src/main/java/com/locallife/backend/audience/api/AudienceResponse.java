package com.locallife.backend.audience.api;

/**
 * Représentation exposée par l'API pour un public (CAT-003). Mêmes
 * principes que {@code CategoryResponse}/{@code ActivityTypeResponse} :
 * porte le {@code code} métier, jamais l'{@code id} technique, avec un
 * {@code label}/{@code description} déjà résolus pour la locale demandée.
 */
public record AudienceResponse(
        String code,
        String label,
        String description,
        boolean active) {
}
