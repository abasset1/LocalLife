package com.locallife.backend.auth.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entité de domaine EmailVerificationToken.
 *
 * <p>{@code tokenHash} contient le SHA-256 (hex) du token envoyé par email à
 * l'utilisateur — le token en clair n'est jamais persisté (voir
 * {@code EmailVerificationService}). Même patron que {@link PasswordResetToken}.
 */
@Table("email_verification_token")
public record EmailVerificationToken(
        @Id Long id,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        boolean used,
        LocalDateTime createdAt) {
}
