package com.locallife.backend.place.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Statut d'une demande d'administration d'un lieu existant (ADR-0012, § 3.1). Les transitions permises sont
 * décrites par {@link ClaimTransitions}.
 */
public enum ClaimStatus {

    /** Rédigée par le professionnel, pas encore envoyée. */
    DRAFT,

    /** Envoyée, en attente d'un modérateur. */
    SUBMITTED,

    /** Prise en charge par un modérateur. */
    IN_REVIEW,

    /** Renvoyée au professionnel pour complément ; il la complète puis la soumet de nouveau. */
    INFO_REQUESTED,

    /** Acceptée : le demandeur gère désormais le lieu. */
    APPROVED,

    /** Refusée, avec un motif. */
    REJECTED,

    /** Retirée par le professionnel. */
    WITHDRAWN;

    /** Statuts d'une demande en cours : une seule par professionnel et par lieu. */
    public static final Set<ClaimStatus> ACTIVE = EnumSet.of(DRAFT, SUBMITTED, IN_REVIEW, INFO_REQUESTED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
