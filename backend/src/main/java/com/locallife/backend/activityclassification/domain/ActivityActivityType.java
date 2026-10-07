package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Type d'activité associé à une activité (CAT-006, SPRINT_CATEG) :
 * relation 1 activité → 0..N types. Même patron que
 * {@link ActivityCategory}.
 */
public record ActivityActivityType(
        @Id Long id,
        Long activityId,
        Long activityTypeId) {
}
