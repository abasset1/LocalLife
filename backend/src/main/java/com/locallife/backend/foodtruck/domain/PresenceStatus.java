package com.locallife.backend.foodtruck.domain;

/** Statut d'une présence de food truck (ADR-0013) ; seule une présence {@link #PUBLISHED} est visible. */
public enum PresenceStatus {
    DRAFT,
    PUBLISHED,
    SUSPENDED,
    ARCHIVED
}
