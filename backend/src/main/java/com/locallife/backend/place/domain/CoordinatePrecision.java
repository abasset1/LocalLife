package com.locallife.backend.place.domain;

/**
 * Précision géographique des coordonnées d'un lieu (ADR-0011, § 2.6 et § 7.1, phase A).
 *
 * <p>Persistée dans {@code location.coordinate_precision} et {@code activity.coordinate_precision} (migration V61),
 * sous son nom.
 */
public enum CoordinatePrecision {

    /** Position du lieu lui-même (bâtiment, entrée). Posée par une validation humaine, jamais par un import. */
    EXACT,

    /** Position estimée : zone, centre de commune, lieu multiple. */
    APPROXIMATE,

    /** Précision non établie. Valeur par défaut, et celle de toutes les données existantes non reconnues. */
    UNKNOWN
}
