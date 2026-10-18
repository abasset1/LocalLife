package com.locallife.backend.user.domain;

/**
 * Rôle d'un utilisateur. Aucune gestion avancée des rôles (ACL) à ce
 * stade — seulement une distinction simple USER / PRO / ADMIN.
 *
 * {@code PRO} (ADR-0012, décision D1) : compte professionnel, attribué par
 * un administrateur ; ouvre l'espace professionnel à travers
 * {@code ProAccessPolicy}, que BILLING-01 rattachera aux abonnements.
 */
public enum Role {
    USER,
    PRO,
    ADMIN
}
