package com.locallife.backend.subscription.domain;

/**
 * Offre LocalLife (BILLING-01, ADR-0014). {@link #FREE} n'est pas un abonnement : c'est l'absence d'abonnement en
 * cours. La consultation de la carte et de la liste est gratuite pour toutes les offres.
 */
public enum Plan {

    /** Découverte et consultation de la carte et de la liste. */
    FREE,

    /** Personnalisation de l'expérience ; aucun avantage payant pour l'instant (décision du 19/10/2026). */
    PREMIUM,

    /** Espace professionnel : gestion de lieux et de food trucks. */
    PRO
}
