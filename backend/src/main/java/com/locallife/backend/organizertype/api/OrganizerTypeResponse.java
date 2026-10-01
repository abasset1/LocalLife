package com.locallife.backend.organizertype.api;

/**
 * Représentation exposée par l'API pour un type d'organisateur (CAT-004).
 * Mêmes principes que les DTO des référentiels précédents : porte le
 * {@code code} métier, jamais l'{@code id} technique, avec un
 * {@code label}/{@code description} déjà résolus pour la locale demandée.
 */
public record OrganizerTypeResponse(
        String code,
        String label,
        String description,
        boolean active) {
}
