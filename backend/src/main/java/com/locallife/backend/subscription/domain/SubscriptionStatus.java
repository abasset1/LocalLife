package com.locallife.backend.subscription.domain;

/** Statut d'un abonnement (BILLING-01, ADR-0014 ; table {@code subscription}). */
public enum SubscriptionStatus {

    /** Demandé par l'utilisateur, en attente d'un administrateur. */
    REQUESTED,

    /** En cours. */
    ACTIVE,

    /** Résilié : l'accès dure jusqu'à la fin de la période en cours. */
    CANCELED,

    /** Période terminée, ou accès retiré par un administrateur. */
    EXPIRED,

    /** Demande refusée par un administrateur. */
    REJECTED,

    /** Demande retirée par l'utilisateur. */
    WITHDRAWN,

    /** Remplacé par une autre offre activée ensuite. */
    REPLACED
}
