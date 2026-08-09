package com.locallife.backend.user.domain;

/**
 * Rôle d'un utilisateur. Aucune gestion avancée des rôles (ACL) à ce
 * stade — seulement une distinction simple USER / ADMIN.
 */
public enum Role {
    USER,
    ADMIN
}
