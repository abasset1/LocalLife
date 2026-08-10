package com.locallife.backend.auth.api;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;

/**
 * Représentation sûre d'un utilisateur, sans {@code passwordHash}. Utilisée
 * comme corps de réponse pour l'inscription : le hash ne doit jamais
 * transiter dans une réponse API, même haché.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.email(), user.role(), user.createdAt());
    }

}
