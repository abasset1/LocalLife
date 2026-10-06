package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Outcome;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Result;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.ReviewReason;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Point unique de décision de classification d'une activité issue d'une
 * source externe (CAT-008, SPRINT_CATEG : « classification centralisée »,
 * « aucun mapping source → catégorie direct non contrôlé »).
 *
 * <p>Règles imposées par le ticket :
 * <ul>
 *   <li>une source ne crée jamais de catégorie : la sortie est toujours un
 *       <b>code V1 existant</b> ou aucun code (résolution en id par
 *       {@code ClassificationService}, qui rejette un code inconnu) ;</li>
 *   <li>cas ambigu → {@code NEEDS_REVIEW}, jamais une classification inventée.</li>
 * </ul>
 *
 * <p>Signaux utilisés (V1) : la catégorie d'origine et les tags de la
 * source, chacun passé par {@link LegacyCategoryMapper}. Le titre et la
 * description ne sont volontairement pas utilisés (risque de faux positifs
 * par mot-clé), ni les types/publics (pas de classification de ces
 * dimensions dans ce ticket).
 *
 * <p>Décision :
 * <ol>
 *   <li>aucun signal → {@code UNCLASSIFIED} ;</li>
 *   <li>un signal du domaine emploi/formation → {@code NEEDS_REVIEW}
 *       ({@code EMPLOYMENT_DOMAIN}), même si un autre signal est classable :
 *       la décision de fond sur ce domaine n'est pas prise ;</li>
 *   <li>plusieurs catégories V1 différentes → {@code NEEDS_REVIEW} (conflit) ;</li>
 *   <li>une seule catégorie V1, et aucun signal ambigu qui l'exclut →
 *       {@code AUTO_CLASSIFIED} ;</li>
 *   <li>sinon {@code NEEDS_REVIEW}.</li>
 * </ol>
 *
 * Classe pure (sans état ni dépendance), déterministe.
 */
public final class ActivityClassifier {

    /** Raison de {@code NEEDS_REVIEW} spécifique à l'agrégation de plusieurs signaux. */
    public static final String CONFLICTING_SIGNALS = "CONFLICTING_SIGNALS";

    /**
     * Décision de classification.
     *
     * @param categoryCode       renseigné uniquement pour {@code AUTO_CLASSIFIED}
     * @param reason             renseigné pour {@code NEEDS_REVIEW}
     * @param candidateCodes     catégories plausibles à départager à la relecture (peut être vide)
     * @param ruleId             règle appliquée, pour la traçabilité
     */
    public record Decision(
            ClassificationStatus status,
            String categoryCode,
            String reason,
            List<String> candidateCodes,
            String ruleId) {
    }

    private ActivityClassifier() {
    }

    public static Decision classify(String sourceCategory, List<String> sourceTags) {
        List<Result> results = new ArrayList<>();
        if (sourceCategory != null && !sourceCategory.isBlank()) {
            results.add(LegacyCategoryMapper.map(sourceCategory));
        }
        if (sourceTags != null) {
            for (String tag : sourceTags) {
                if (tag != null && !tag.isBlank()) {
                    results.add(LegacyCategoryMapper.map(tag));
                }
            }
        }
        if (results.isEmpty()) {
            return new Decision(ClassificationStatus.UNCLASSIFIED, null, null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }

        Set<String> mapped = new LinkedHashSet<>();
        List<Result> ambiguous = new ArrayList<>();
        boolean employment = false;
        for (Result result : results) {
            if (result.outcome() == Outcome.MAPPED) {
                mapped.add(result.categoryCode());
            } else if (result.outcome() == Outcome.NEEDS_REVIEW) {
                if (result.reason() == ReviewReason.EMPLOYMENT_DOMAIN) {
                    employment = true;
                } else if (result.reason() == ReviewReason.AMBIGUOUS_CATEGORY) {
                    ambiguous.add(result);
                }
            }
        }

        if (employment) {
            return review(ReviewReason.EMPLOYMENT_DOMAIN.name(), List.copyOf(mapped));
        }
        if (mapped.size() > 1) {
            return review(CONFLICTING_SIGNALS, List.copyOf(mapped));
        }
        if (mapped.size() == 1) {
            String code = mapped.iterator().next();
            // Un signal ambigu qui ne contient pas ce code contredit la classification.
            boolean contradicted = ambiguous.stream()
                    .anyMatch(result -> !result.candidateCategoryCodes().contains(code));
            if (contradicted) {
                Set<String> candidates = new LinkedHashSet<>(mapped);
                ambiguous.forEach(result -> candidates.addAll(result.candidateCategoryCodes()));
                return review(CONFLICTING_SIGNALS, List.copyOf(candidates));
            }
            return new Decision(ClassificationStatus.AUTO_CLASSIFIED, code, null, List.of(), "AUTO:" + code);
        }
        if (!ambiguous.isEmpty()) {
            Set<String> candidates = new LinkedHashSet<>();
            ambiguous.forEach(result -> candidates.addAll(result.candidateCategoryCodes()));
            return review(ReviewReason.AMBIGUOUS_CATEGORY.name(), List.copyOf(candidates));
        }
        // Des signaux existent mais aucun n'est une catégorie (public, type, lieu, texte libre...).
        ReviewReason reason = results.stream()
                .map(Result::reason)
                .filter(r -> r != null)
                .findFirst()
                .orElse(ReviewReason.UNRECOGNIZED);
        return review(reason.name(), List.of());
    }

    private static Decision review(String reason, List<String> candidates) {
        return new Decision(ClassificationStatus.NEEDS_REVIEW, null, reason, candidates, "REVIEW:" + reason);
    }
}
