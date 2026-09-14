package com.locallife.backend.user.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Lien entre un compte {@link User} et une identité externe (LL-EF-011),
 * par exemple un compte Google. Voir
 * {@code V28__create_user_identities_table.sql} pour le détail des
 * décisions de modélisation (table séparée, {@code provider_user_id}
 * plutôt que l'email, {@code ON DELETE CASCADE}).
 *
 * {@code providerUserId} est l'identifiant stable côté provider — pour
 * Google, le claim {@code sub} du token OpenID Connect vérifié par
 * {@code GoogleAuthService}, jamais l'email (qui peut changer côté
 * Google).
 */
@Table("user_identities")
public record UserIdentity(
        @Id Long id,
        Long userId,
        AuthProvider provider,
        String providerUserId,
        LocalDateTime createdAt) {
}
