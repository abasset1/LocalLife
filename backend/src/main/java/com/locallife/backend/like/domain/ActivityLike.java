package com.locallife.backend.like.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Relation "like" entre un {@code User} et une {@code Activity}
 * (LS-002). Référence les deux uniquement par id (pas d'import du
 * type {@code Activity}/{@code User} ici) : ce module ne dépend
 * d'aucun autre module au niveau du domaine, conformément à
 * `ARCHITECTURE.md` — voir {@code LikeService} pour où ces ids sont
 * résolus en entités complètes.
 *
 * Voir {@code V29__create_activity_likes_table.sql} pour le détail des
 * décisions de modélisation (contrainte d'unicité, {@code ON DELETE
 * CASCADE}).
 */
@Table("activity_likes")
public record ActivityLike(
        @Id Long id,
        Long userId,
        Long activityId,
        LocalDateTime createdAt) {
}
