package com.locallife.backend.auth.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entité de domaine PasswordResetToken (LL-EF-007).
 *
 * <p>{@code tokenHash} contient le SHA-256 (hex) du token de réinitialisation
 * envoyé par email à l'utilisateur — le token en clair n'est jamais persisté
 * (voir {@code PasswordResetService}).
 */
@Table("password_reset_token")
public record PasswordResetToken(
        @Id Long id,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        boolean used,
        LocalDateTime createdAt) {
}
