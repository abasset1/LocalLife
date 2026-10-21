package com.locallife.backend.user.domain;

/**
 * Statut d'un compte (ACC-02, V69), distinct du rôle ({@link Role}) et de l'offre commerciale (abonnement,
 * ADR-0014). Un compte {@link #SUSPENDED} garde toutes ses données mais ne peut ni se connecter ni utiliser une
 * route authentifiée : le statut est relu en base à chaque requête ({@code JwtFilter}).
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED
}
