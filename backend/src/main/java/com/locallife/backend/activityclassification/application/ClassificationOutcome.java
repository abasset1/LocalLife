package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import java.util.List;

/**
 * Résultat de {@link ClassificationService#decide} : la décision du
 * classifieur et, si elle est exploitable, les ids des catégories V1
 * correspondantes (1..N, issue #45).
 *
 * @param categoryIds catégories existantes et actives, jamais {@code null},
 *                    vide si l'élément n'est pas classé
 */
public record ClassificationOutcome(Decision decision, List<Long> categoryIds) {

    /** Vrai si au moins une catégorie existante a été déterminée (activité créable). */
    public boolean classified() {
        return !categoryIds.isEmpty() && decision.status().hasActivity();
    }
}
