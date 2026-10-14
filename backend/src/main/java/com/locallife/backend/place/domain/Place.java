package com.locallife.backend.place.domain;

import java.time.OffsetDateTime;

/**
 * Lieu permanent (ADR-0011, phase B) : ligne de {@code location} qui porte un type et un statut de modération.
 * Vue de lecture, construite par {@code PlaceRepository} ; un lieu d'événement n'est jamais un {@code Place}.
 */
public record Place(
        Long id,
        String name,
        String placeType,
        PlaceStatus status,
        String address,
        String postalCode,
        String city,
        Double latitude,
        Double longitude,
        String description,
        String website,
        CoordinatePrecision coordinatePrecision,
        OffsetDateTime publishedAt,
        OffsetDateTime moderatedAt) {
}
