package com.locallife.backend.activityclassification.domain;

import org.springframework.data.annotation.Id;

/**
 * Type d'organisateur d'une activité (CAT-006, SPRINT_CATEG) : relation
 * 1 activité → 0..1 type d'organisateur — nullable par nature (le ticket
 * l'exprime explicitement comme 0..1, contrairement à la catégorie
 * principale), une activité peut donc n'avoir aucune ligne ici de façon
 * définitive, pas seulement temporairement (voir
 * {@code activity.primary_category_id}, {@code NOT NULL}, pour la distinction).
 */
public record ActivityOrganizerType(
        @Id Long id,
        Long activityId,
        Long organizerTypeId) {
}
