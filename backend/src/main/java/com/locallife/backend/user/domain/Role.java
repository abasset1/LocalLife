package com.locallife.backend.user.domain;

/**
 * Rôle d'un utilisateur dans Neiri (décision du 02/11/2026) : ce qu'il peut faire dans l'application, distinct de
 * son type de compte (offre Gratuit, Premium ou Pro, voir {@code Plan}).
 * <ul>
 *   <li>{@code USER} : utilisateur ;</li>
 *   <li>{@code LOCALLIFE} : membre de l'équipe Neiri, accès à la modération de l'administration (activités,
 *       lieux, demandes d'offre Pro, food trucks), sans les actions sensibles (comptes, rôles, suppression,
 *       imports, sources, catégories, e-mails, journal) ;</li>
 *   <li>{@code ADMIN} : administrateur, tous les droits.</li>
 * </ul>
 * L'ancien rôle {@code PRO} est devenu un type de compte : un abonnement Pro (V81).
 */
public enum Role {
    USER,
    LOCALLIFE,
    ADMIN;

    /** Équipe Neiri ou administrateur : contributions sans limite et sans offre. */
    public boolean isStaff() {
        return this == LOCALLIFE || this == ADMIN;
    }
}
