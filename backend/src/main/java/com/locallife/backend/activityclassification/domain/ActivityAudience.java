package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Public associé à une activité (CAT-006, SPRINT_CATEG) : relation
 * 1 activité → 0..N publics. Même patron que
 * {@link ActivitySecondaryCategory}.
 */
public record ActivityAudience(
        @Id Long id,
        Long activityId,
        Long audienceId) {
}
