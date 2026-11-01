package com.locallife.backend.follow.domain;

import java.time.OffsetDateTime;

/**
 * Activité ou lieu suivi par un utilisateur, pour la liste « Activités et lieux suivis ». {@code status} : statut de
 * l'activité ({@code PUBLISHED}, {@code CANCELLED}…) ou du lieu ({@code PUBLISHED}, {@code ARCHIVED}…).
 */
public record FollowedItem(FollowTargetType type, Long targetId, String title, String city, String status,
        OffsetDateTime followedAt) {
}
