package com.locallife.backend.user.api;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;

/**
 * Profil de l'utilisateur connecté ({@code GET/PATCH /api/v1/users/me}) : les champs de {@code UserResponse}, plus ce
 * dont les interfaces ont besoin pour proposer les bonnes actions. Aucun secret.
 *
 * @param pendingEmail  nouvelle adresse en attente de confirmation, {@code null} sinon
 * @param hasPassword   faux pour un compte créé avec Google sans mot de passe LocalLife
 * @param canContribute droit de proposer ou modifier une activité (offre Premium ou Pro, rôle PRO ou ADMIN)
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        Role role,
        LocalDateTime createdAt,
        String pendingEmail,
        boolean hasPassword,
        boolean canContribute) {

    static CurrentUserResponse from(User user, String pendingEmail, boolean hasPassword, boolean canContribute) {
        return new CurrentUserResponse(user.id(), user.username(), user.email(), user.role(), user.createdAt(),
                pendingEmail, hasPassword, canContribute);
    }
}
