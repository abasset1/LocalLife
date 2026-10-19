package com.locallife.backend.foodtruck.domain;

/**
 * Statut d'un food truck ({@code food_truck.status}). Seul {@link #PUBLISHED} est visible publiquement.
 * {@code PENDING}/{@code REJECTED} (LL-6009) ne sont plus produits, gardés pour les lignes anciennes.
 */
public enum FoodTruckStatus {

    /** Créé par son propriétaire, pas encore publié (PRO-01 lot C). */
    DRAFT,

    PENDING,

    /** Publié par son propriétaire : aucune modération préalable (ADR-0012, décision D6). */
    PUBLISHED,

    /** Suspendu par son propriétaire, ou par un administrateur (il ne peut alors plus le republier). */
    SUSPENDED,

    /** Archivé par son propriétaire : définitif. */
    ARCHIVED,

    REJECTED
}
