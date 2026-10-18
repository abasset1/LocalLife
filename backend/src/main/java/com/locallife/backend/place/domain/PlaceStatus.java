package com.locallife.backend.place.domain;

/**
 * Statut de modération d'un lieu permanent ({@code location.place_status}, ADR-0011, phase B). Un lieu d'événement
 * n'en a pas. Seul {@link #PUBLISHED} est visible comme lieu ; aucun statut ne masque un événement.
 */
public enum PlaceStatus {

    /** Créé par un professionnel, pas encore soumis à la modération (ADR-0012) ; visible de ses seuls gestionnaires. */
    DRAFT,

    /** Importé, en attente de modération. Tout nouveau lieu importé l'est. */
    PENDING,

    /** Validé par un administrateur. */
    PUBLISHED,

    /** Refusé par un administrateur : jamais reproposé, même s'il est réimporté. */
    REJECTED,

    /** Disparu de la source. */
    ARCHIVED
}
