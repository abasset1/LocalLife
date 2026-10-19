package com.locallife.backend.foodtruck.domain;

/**
 * Présence vue par le propriétaire du food truck (ADR-0013, FOODTRUCK-01) : tous statuts, avec l'emplacement
 * ({@code location}) qu'elle utilise.
 */
public record ManagedPresence(FoodTruckPresence presence, PresenceStatus status, Long locationId) {
}
