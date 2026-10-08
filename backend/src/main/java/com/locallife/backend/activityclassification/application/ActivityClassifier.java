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
 * <p>Décision (issue #45 : une activité a 1..N catégories, sans principale ni
 * secondaire, sans score ni arbitrage) :
 * <ol>
 *   <li>aucun signal → {@code UNCLASSIFIED} ;</li>
 *   <li>une ou plusieurs catégories V1 → {@code AUTO_CLASSIFIED} avec
 *       <b>toutes</b> ces catégories, dans l'ordre des signaux (catégorie
 *       d'origine puis tags). Plusieurs catégories ne sont jamais un conflit ;
 *       un signal ambigu (ex. {@code patrimoine} : CULTURE ou TOURISM) ne
 *       désigne aucune catégorie et n'en ajoute donc aucune. Le domaine
 *       emploi/formation est une catégorie comme les autres ({@code EMPLOYMENT},
 *       issue #49) : il s'ajoute aux autres catégories détectées ;</li>
 *   <li>seulement des signaux ambigus → {@code NEEDS_REVIEW}
 *       ({@code AMBIGUOUS_CATEGORY}, candidats listés) ;</li>
 *   <li>sinon {@code NEEDS_REVIEW} (public, type, valeur inconnue...).</li>
 * </ol>
 *
 * Classe pure (sans état ni dépendance), déterministe.
 */
public final class ActivityClassifier {

    /**
     * Décision de classification.
     *
     * @param categoryCodes      codes V1 retenus (1..N), renseignés uniquement pour
     *                           {@code AUTO_CLASSIFIED}, sinon vide ; ordre de détection
     * @param reason             renseigné pour {@code NEEDS_REVIEW}
     * @param candidateCodes     catégories plausibles à départager à la relecture (peut être vide)
     * @param ruleId             règle appliquée, pour la traçabilité
     * @param source             origine de la décision : {@code null} pour les keywords source,
     *                           {@code TITLE_KEYWORD} pour le fallback titre
     * @param matchedKeywords    mot(s)-clé(s) du référentiel reconnu(s) (fallback titre), sinon vide
     */
    public record Decision(
            ClassificationStatus status,
            List<String> categoryCodes,
            String reason,
            List<String> candidateCodes,
            String ruleId,
            String source,
            List<String> matchedKeywords) {

        /** Décision issue des keywords source (sans source dédiée ni mot-clé du référentiel). */
        public Decision(
                ClassificationStatus status, List<String> categoryCodes, String reason, List<String> candidateCodes,
                String ruleId) {
            this(status, categoryCodes, reason, candidateCodes, ruleId, null, List.of());
        }
    }

    /** Identifiant de règle d'une classification automatique : {@code AUTO:CODE1+CODE2...}. */
    static String autoRuleId(List<String> categoryCodes) {
        return "AUTO:" + String.join("+", categoryCodes);
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
            return new Decision(
                    ClassificationStatus.UNCLASSIFIED, List.of(), null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }

        Set<String> mapped = new LinkedHashSet<>();
        Set<String> ambiguousCandidates = new LinkedHashSet<>();
        for (Result result : results) {
            if (result.outcome() == Outcome.MAPPED) {
                mapped.add(result.categoryCode());
            } else if (result.reason() == ReviewReason.AMBIGUOUS_CATEGORY) {
                ambiguousCandidates.addAll(result.candidateCategoryCodes());
            }
        }

        if (!mapped.isEmpty()) {
            List<String> codes = List.copyOf(mapped);
            return new Decision(ClassificationStatus.AUTO_CLASSIFIED, codes, null, List.of(), autoRuleId(codes));
        }
        if (!ambiguousCandidates.isEmpty()) {
            return review(ReviewReason.AMBIGUOUS_CATEGORY.name(), List.copyOf(ambiguousCandidates));
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
        return new Decision(ClassificationStatus.NEEDS_REVIEW, List.of(), reason, candidates, "REVIEW:" + reason);
    }
}
