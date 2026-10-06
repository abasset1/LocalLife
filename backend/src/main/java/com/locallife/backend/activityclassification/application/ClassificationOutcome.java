package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;

/**
 * Résultat de {@link ClassificationService#decide} : la décision du
 * classifieur et, si elle est exploitable, l'id de la catégorie V1
 * correspondante.
 *
 * @param primaryCategoryId {@code null} si l'élément n'est pas classé
 */
public record ClassificationOutcome(Decision decision, Long primaryCategoryId) {

    /** Vrai si une catégorie principale existante a été déterminée (activité créable). */
    public boolean classified() {
        return primaryCategoryId != null && decision.status().hasActivity();
    }
}
