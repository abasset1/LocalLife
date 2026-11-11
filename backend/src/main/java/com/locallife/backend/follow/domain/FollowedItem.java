package com.locallife.backend.follow.domain;

import java.time.OffsetDateTime;

/**
 * Activité, lieu ou food truck suivi par un utilisateur, pour la liste des suivis. {@code status} : statut de
 * l'activité ({@code PUBLISHED}, {@code CANCELLED}…), du lieu ou du food truck ({@code PUBLISHED}, {@code ARCHIVED}…).
 */
public record FollowedItem(FollowTargetType type, Long targetId, String title, String city, String status,
        OffsetDateTime followedAt) {
}
