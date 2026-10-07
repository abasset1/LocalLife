package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import java.util.List;

/**
 * Résultat de {@link ClassificationService#decide} : la décision du
 * classifieur et, si elle est exploitable, l'id de la catégorie V1
 * correspondante.
 *
 * @param primaryCategoryId    {@code null} si l'élément n'est pas classé
 * @param secondaryCategoryIds catégories secondaires existantes et actives (issue #43), jamais
 *                             {@code null}, vide si l'élément n'est pas classé
 */
public record ClassificationOutcome(Decision decision, Long primaryCategoryId, List<Long> secondaryCategoryIds) {

    /** Résultat sans catégorie secondaire. */
    public ClassificationOutcome(Decision decision, Long primaryCategoryId) {
        this(decision, primaryCategoryId, List.of());
    }

    /** Vrai si une catégorie principale existante a été déterminée (activité créable). */
    public boolean classified() {
        return primaryCategoryId != null && decision.status().hasActivity();
    }
}
