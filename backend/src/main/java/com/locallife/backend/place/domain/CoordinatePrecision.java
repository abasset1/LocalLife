package com.locallife.backend.place.domain;

/**
 * Précision géographique des coordonnées d'un lieu (ADR-0011, § 2.6 et § 7.1, phase A).
 *
 * <p>Aucune valeur n'est encore persistée : la colonne {@code coordinate_precision} est un projet de
 * migration, non appliqué tant que l'implémentation détaillée n'est pas validée.
 */
public enum CoordinatePrecision {

    /** Position du lieu lui-même (bâtiment, entrée). Posée par une validation humaine, jamais par un import. */
    EXACT,

    /** Position estimée : zone, centre de commune, lieu multiple. */
    APPROXIMATE,

    /** Précision non établie. Valeur par défaut, et celle de toutes les données existantes non reconnues. */
    UNKNOWN
}
