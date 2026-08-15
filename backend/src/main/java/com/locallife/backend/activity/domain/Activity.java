package com.locallife.backend.activity.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Activity.
 *
 * {@code sourceId} et {@code importKey} ajoutés en LL-5008 : lien vers la
 * {@code Source} d'origine (voir {@code source} module), obligatoire pour
 * toute activité — y compris les créations manuelles, rattachées à la
 * source réservée {@code MANUAL} (voir {@code SOURCE_CONTRACT.md} et
 * {@code ActivityService#createActivity}). {@code importKey} est la clé
 * de déduplication calculée par {@code DeduplicationService} (LL-5007) ;
 * {@code null} pour les activités créées manuellement (aucune donnée
 * collectée à déduplicer), non nul pour les activités importées, unique
 * par {@code sourceId} (voir {@code V9__link_activity_to_source.sql}).
 */
public record Activity(
        @Id Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        Long sourceId,
        String importKey) {
}
