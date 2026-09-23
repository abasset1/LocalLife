package com.locallife.backend.user.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entité de domaine User. Aucune relation avec d'autres entités.
 *
 * {@code emailVerified} : {@code false} pour un compte créé par
 * inscription classique tant que l'adresse n'a pas été confirmée via le lien
 * envoyé par email (voir {@code EmailVerificationService}) ; {@code true}
 * pour un compte dont la propriété de l'adresse est déjà prouvée (Google,
 * administrateur initial, comptes antérieurs à la migration V31).
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
        String passwordHash,
        Role role,
        LocalDateTime createdAt,
        boolean emailVerified) {
}
