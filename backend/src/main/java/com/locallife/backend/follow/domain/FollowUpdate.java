package com.locallife.backend.follow.domain;

import java.util.List;

/**
 * Mise à jour à notifier pour un suivi : changements enregistrés depuis le dernier envoi ({@code kinds}, voir
 * {@code V79__follows.sql}) et, pour un lieu, titres des activités publiées depuis ({@code newActivities}).
 * {@code maxChangeId} : dernier changement couvert, à mémoriser après l'envoi.
 */
public record FollowUpdate(Long followId, FollowTargetType type, Long targetId, String title, List<String> kinds,
        List<String> newActivities, long maxChangeId) {

    public boolean isEmpty() {
        return kinds.isEmpty() && newActivities.isEmpty();
    }
}
