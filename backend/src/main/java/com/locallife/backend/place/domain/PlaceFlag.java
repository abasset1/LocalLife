package com.locallife.backend.place.domain;

/** Signalement calculé pour la modération (ADR-0011, § 7.2.2, règle 7) : non bloquant, aide à la relecture. */
public enum PlaceFlag {

    /** Le lieu n'a pas d'adresse. */
    NO_ADDRESS,

    /** Le nom est celui d'un itinéraire (« Circuit… », « Parcours… »), pas d'un lieu. */
    ROUTE_NAME,

    /** Un autre lieu permanent porte le même nom dans la même commune (code postal). */
    DUPLICATE_NAME,

    /** Position approximative (ADR-0011, phase A). */
    APPROXIMATE_POSITION
}
