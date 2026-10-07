package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Outcome;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Result;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.ReviewReason;
import com.locallife.backend.activityclassification.application.PrimaryCategorySelector.Selection;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
 * <p>Décision (issue #43 : 1 catégorie principale + 0..N secondaires) :
 * <ol>
 *   <li>aucun signal → {@code UNCLASSIFIED} ;</li>
 *   <li>un signal du domaine emploi/formation → {@code NEEDS_REVIEW}
 *       ({@code EMPLOYMENT_DOMAIN}), même si un autre signal est classable :
 *       la décision de fond sur ce domaine n'est pas prise ;</li>
 *   <li>un signal ambigu dont aucune catégorie candidate n'est détectée par
 *       ailleurs → {@code NEEDS_REVIEW} ({@code CONFLICTING_SIGNALS}) : son
 *       domaine reste indéterminé et pourrait être le principal ;</li>
 *   <li>une ou plusieurs catégories V1 → {@code AUTO_CLASSIFIED} : principal
 *       et secondaires choisis par {@link PrimaryCategorySelector}. Score d'une
 *       catégorie = priorité maximale de ses signaux, lue dans
 *       {@code category_keyword} (valeur source égale à un mot-clé de la même
 *       catégorie après {@link LegacyCategoryMapper#normalize}), sinon
 *       {@link CategoryKeyword#DEFAULT_PRIORITY}. Une égalité est départagée par
 *       la catégorie d'origine de la source ;</li>
 *   <li>égalité non départageable → {@code NEEDS_REVIEW} ({@code AMBIGUOUS_PRIMARY}) ;</li>
 *   <li>sinon {@code NEEDS_REVIEW}.</li>
 * </ol>
 *
 * Classe pure (sans état ni dépendance), déterministe.
 */
public final class ActivityClassifier {

    /**
     * Raison de {@code NEEDS_REVIEW} : un signal ambigu (plusieurs catégories possibles) n'est
     * compatible avec aucune catégorie détectée. N'est plus produite par la seule présence de
     * plusieurs catégories valides (issue #43).
     */
    public static final String CONFLICTING_SIGNALS = "CONFLICTING_SIGNALS";

    /**
     * Raison de {@code NEEDS_REVIEW} : plusieurs catégories détectées à égalité de priorité, sans
     * moyen déterministe de choisir la principale (candidats = ex aequo).
     */
    public static final String AMBIGUOUS_PRIMARY = "AMBIGUOUS_PRIMARY";

    /**
     * Décision de classification.
     *
     * @param categoryCode       renseigné uniquement pour {@code AUTO_CLASSIFIED}
     * @param reason             renseigné pour {@code NEEDS_REVIEW}
     * @param candidateCodes     catégories plausibles à départager à la relecture (peut être vide)
     * @param ruleId             règle appliquée, pour la traçabilité
     * @param source             origine de la décision : {@code null} pour les keywords source,
     *                           {@code TITLE_KEYWORD} pour le fallback titre
     * @param matchedKeywords    mot(s)-clé(s) du référentiel reconnu(s) (fallback titre), sinon vide
     * @param secondaryCategoryCodes catégories secondaires (codes V1), renseignées uniquement pour
     *                           {@code AUTO_CLASSIFIED}, jamais {@code categoryCode} ; peut être vide
     */
    public record Decision(
            ClassificationStatus status,
            String categoryCode,
            String reason,
            List<String> candidateCodes,
            String ruleId,
            String source,
            List<String> matchedKeywords,
            List<String> secondaryCategoryCodes) {

        /** Décision issue des keywords source (sans source dédiée ni mot-clé du référentiel). */
        public Decision(
                ClassificationStatus status, String categoryCode, String reason, List<String> candidateCodes,
                String ruleId) {
            this(status, categoryCode, reason, candidateCodes, ruleId, null, List.of());
        }

        /** Décision sans catégorie secondaire. */
        public Decision(
                ClassificationStatus status, String categoryCode, String reason, List<String> candidateCodes,
                String ruleId, String source, List<String> matchedKeywords) {
            this(status, categoryCode, reason, candidateCodes, ruleId, source, matchedKeywords, List.of());
        }
    }

    private ActivityClassifier() {
    }

    /** Priorités par défaut pour tous les signaux (aucun référentiel de mots-clés). */
    public static Decision classify(String sourceCategory, List<String> sourceTags) {
        return classify(sourceCategory, sourceTags, List::of);
    }

    /**
     * @param keywordEntries référentiel {@code category_keyword} pour la priorité des signaux ; lu
     *                       uniquement si plusieurs catégories sont détectées
     */
    public static Decision classify(
            String sourceCategory, List<String> sourceTags, Supplier<List<KeywordEntry>> keywordEntries) {
        List<Signal> signals = new ArrayList<>();
        if (sourceCategory != null && !sourceCategory.isBlank()) {
            signals.add(new Signal(sourceCategory, LegacyCategoryMapper.map(sourceCategory)));
        }
        if (sourceTags != null) {
            for (String tag : sourceTags) {
                if (tag != null && !tag.isBlank()) {
                    signals.add(new Signal(tag, LegacyCategoryMapper.map(tag)));
                }
            }
        }
        if (signals.isEmpty()) {
            return new Decision(ClassificationStatus.UNCLASSIFIED, null, null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }

        List<Signal> mapped = new ArrayList<>();
        List<Result> ambiguous = new ArrayList<>();
        boolean employment = false;
        for (Signal signal : signals) {
            Result result = signal.result();
            if (result.outcome() == Outcome.MAPPED) {
                mapped.add(signal);
            } else if (result.outcome() == Outcome.NEEDS_REVIEW) {
                if (result.reason() == ReviewReason.EMPLOYMENT_DOMAIN) {
                    employment = true;
                } else if (result.reason() == ReviewReason.AMBIGUOUS_CATEGORY) {
                    ambiguous.add(result);
                }
            }
        }
        Set<String> mappedCodes = new LinkedHashSet<>();
        mapped.forEach(signal -> mappedCodes.add(signal.result().categoryCode()));

        if (employment) {
            return review(ReviewReason.EMPLOYMENT_DOMAIN.name(), List.copyOf(mappedCodes));
        }
        if (!mappedCodes.isEmpty()) {
            // Un signal ambigu qui ne recoupe aucune catégorie détectée laisse un domaine indéterminé.
            boolean contradicted = ambiguous.stream().anyMatch(result ->
                    result.candidateCategoryCodes().stream().noneMatch(mappedCodes::contains));
            if (contradicted) {
                Set<String> candidates = new LinkedHashSet<>(mappedCodes);
                ambiguous.forEach(result -> candidates.addAll(result.candidateCategoryCodes()));
                return review(CONFLICTING_SIGNALS, List.copyOf(candidates));
            }
            return decideAmongMapped(mapped, mappedCodes, sourceCategory, keywordEntries);
        }
        if (!ambiguous.isEmpty()) {
            Set<String> candidates = new LinkedHashSet<>();
            ambiguous.forEach(result -> candidates.addAll(result.candidateCategoryCodes()));
            return review(ReviewReason.AMBIGUOUS_CATEGORY.name(), List.copyOf(candidates));
        }
        // Des signaux existent mais aucun n'est une catégorie (public, type, lieu, texte libre...).
        ReviewReason reason = signals.stream()
                .map(signal -> signal.result().reason())
                .filter(r -> r != null)
                .findFirst()
                .orElse(ReviewReason.UNRECOGNIZED);
        return review(reason.name(), List.of());
    }

    private static Decision decideAmongMapped(
            List<Signal> mapped, Set<String> mappedCodes, String sourceCategory,
            Supplier<List<KeywordEntry>> keywordEntries) {
        if (mappedCodes.size() == 1) {
            String code = mappedCodes.iterator().next();
            return new Decision(ClassificationStatus.AUTO_CLASSIFIED, code, null, List.of(), "AUTO:" + code);
        }
        List<KeywordEntry> entries = keywordEntries.get();
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Signal signal : mapped) {
            String code = signal.result().categoryCode();
            scores.merge(code, priorityOf(signal.value(), code, entries), Math::max);
        }
        String preferred = sourceCategory == null ? null : LegacyCategoryMapper.map(sourceCategory).categoryCode();
        Selection selection = PrimaryCategorySelector.select(scores, preferred);
        if (!selection.decided()) {
            return review(AMBIGUOUS_PRIMARY, selection.tiedCodes());
        }
        String code = selection.primaryCode();
        return new Decision(ClassificationStatus.AUTO_CLASSIFIED, code, null, List.of(), "AUTO:" + code,
                null, List.of(), selection.secondaryCodes());
    }

    /**
     * Priorité d'un signal source : celle du mot-clé de la même catégorie égal à la valeur (après
     * normalisation, comme la correspondance exacte de {@link LegacyCategoryMapper}), sinon la priorité
     * par défaut.
     */
    private static int priorityOf(String value, String code, List<KeywordEntry> entries) {
        String key = LegacyCategoryMapper.normalize(value);
        return entries == null ? CategoryKeyword.DEFAULT_PRIORITY : entries.stream()
                .filter(entry -> code.equals(entry.categoryCode()))
                .filter(entry -> key.equals(LegacyCategoryMapper.normalize(entry.keyword())))
                .mapToInt(KeywordEntry::priority)
                .max()
                .orElse(CategoryKeyword.DEFAULT_PRIORITY);
    }

    private record Signal(String value, Result result) {
    }

    private static Decision review(String reason, List<String> candidates) {
        return new Decision(ClassificationStatus.NEEDS_REVIEW, null, reason, candidates, "REVIEW:" + reason);
    }
}
