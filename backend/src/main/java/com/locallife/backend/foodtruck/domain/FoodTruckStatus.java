package com.locallife.backend.foodtruck.domain;

/**
 * Statut d'un food truck ({@code food_truck.status}). Seul {@link #PUBLISHED} est visible publiquement.
 */
public enum FoodTruckStatus {

    /** Créé par son propriétaire, pas encore publié (PRO-01 lot C). */
    DRAFT,

    /** Première publication demandée, en attente de validation par l'équipe (décision du 22/10/2026). */
    PENDING,

    /** Publié : validé une première fois par l'équipe, puis publié ou republié par son propriétaire. */
    PUBLISHED,

    /**
     * Suspendu par son propriétaire, par un administrateur (il ne peut alors plus le republier), ou parce que
     * l'accès professionnel de son propriétaire a pris fin (republié s'il revient).
     */
    SUSPENDED,

    /** Archivé par son propriétaire : définitif. */
    ARCHIVED,

    /** Première publication refusée par l'équipe, avec un motif ; le propriétaire peut corriger et renvoyer. */
    REJECTED
}
