package com.locallife.backend.user.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entité de domaine User. Aucune relation avec d'autres entités.
 *
 * Mappée explicitement sur la table {@code users} : {@code user} est un mot
 * réservé en PostgreSQL et ne peut pas être utilisé comme nom de table sans
 * être quoté à chaque requête.
 */
@Table("users")
public record User(
        @Id Long id,
        String username,
        String email,
        LocalDateTime createdAt) {
}
