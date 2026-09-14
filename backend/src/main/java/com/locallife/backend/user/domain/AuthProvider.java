package com.locallife.backend.user.domain;

/**
 * Provider d'authentification externe pouvant être lié à un compte
 * {@link User} (LL-EF-011). {@code LOCAL} n'y figure volontairement pas :
 * l'authentification classique (email + mot de passe) reste portée
 * directement par {@code User#passwordHash}, déjà en place avant ce
 * ticket — {@link AuthProvider} ne sert qu'à qualifier les entrées de
 * {@link UserIdentity}, une par identité externe liée. Voir
 * {@code V28__create_user_identities_table.sql} pour le détail de cette
 * décision.
 */
public enum AuthProvider {
    GOOGLE
}
