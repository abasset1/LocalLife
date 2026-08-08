package com.locallife.backend.user.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine User. Aucune relation avec d'autres entités.
 */
public record User(
        @Id Long id,
        String username,
        String email,
        LocalDateTime createdAt) {
}
