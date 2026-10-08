package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Décision de classification d'un élément de source à partir de ses keywords : catégorie d'origine et tags
 * (CAT-008, « classification centralisée »).
 *
 * <p><b>Référentiel unique (issue #50)</b> : chaque signal source est comparé au référentiel en base
 * {@code category_keyword} (portées {@code ALL} et {@code SOURCE}), avec la <b>même règle</b> que le titre
 * ({@link KeywordCategoryMatcher}). Aucun mot-clé métier n'est codé en Java ; l'ancienne liste fermée
 * {@code LegacyCategoryMapper} a été reprise en base (V45) puis supprimée.
 *
 * <p>Règles :
 * <ul>
 *   <li>une source ne crée jamais de catégorie : la sortie est toujours un ou plusieurs codes du
 *       référentiel, résolus en ids par {@code ClassificationService} (qui écarte un code inconnu) ;</li>
 *   <li>aucun signal (ni catégorie d'origine, ni tag non blanc) → {@code UNCLASSIFIED} ;</li>
 *   <li>une ou plusieurs catégories reconnues → {@code AUTO_CLASSIFIED} avec <b>toutes</b> ces catégories,
 *       dans l'ordre des signaux (catégorie d'origine puis tags) ; plusieurs catégories ne sont jamais un
 *       conflit (issue #45) ;</li>
 *   <li>des signaux, mais aucun ne correspond à un mot-clé (public, format, lieu, nom propre...) →
 *       {@code NEEDS_REVIEW} ({@link #UNRECOGNIZED}) : jamais une classification inventée.</li>
 * </ul>
 *
 * Classe pure (sans état ni dépendance), déterministe.
 */
public final class ActivityClassifier {

    /** Origine tracée dans {@code classification_record.classification_source} pour les keywords source. */
    public static final String SOURCE = "SOURCE_KEYWORD";

    /** Raison de {@code NEEDS_REVIEW} : la source fournit des keywords, mais aucun n'est dans le référentiel. */
    public static final String UNRECOGNIZED = "UNRECOGNIZED";

    /**
     * Décision de classification.
     *
     * @param categoryCodes      codes retenus (1..N), renseignés uniquement pour
     *                           {@code AUTO_CLASSIFIED}, sinon vide ; ordre de détection
     * @param reason             renseigné pour {@code NEEDS_REVIEW}
     * @param candidateCodes     catégories plausibles à départager à la relecture (peut être vide)
     * @param ruleId             règle appliquée, pour la traçabilité
     * @param source             origine de la décision : {@link #SOURCE} pour les keywords source,
     *                           {@code TITLE_KEYWORD} pour le fallback titre, {@code null} sans signal
     * @param matchedKeywords    mot(s)-clé(s) du référentiel reconnu(s), sinon vide
     */
    public record Decision(
            ClassificationStatus status,
            List<String> categoryCodes,
            String reason,
            List<String> candidateCodes,
            String ruleId,
            String source,
            List<String> matchedKeywords) {

        /** Décision sans origine ni mot-clé reconnu. */
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

    /**
     * @param keywordEntries référentiel {@code category_keyword} actif (portées {@code ALL} et {@code SOURCE})
     */
    public static Decision classify(String sourceCategory, List<String> sourceTags, List<KeywordEntry> keywordEntries) {
        List<String> signals = new ArrayList<>();
        if (sourceCategory != null && !sourceCategory.isBlank()) {
            signals.add(sourceCategory);
        }
        if (sourceTags != null) {
            for (String tag : sourceTags) {
                if (tag != null && !tag.isBlank()) {
                    signals.add(tag);
                }
            }
        }
        if (signals.isEmpty()) {
            return new Decision(
                    ClassificationStatus.UNCLASSIFIED, List.of(), null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }

        Set<String> codes = new LinkedHashSet<>();
        Set<String> matched = new LinkedHashSet<>();
        for (String signal : signals) {
            KeywordCategoryMatcher.Match match = KeywordCategoryMatcher.match(signal, keywordEntries);
            codes.addAll(match.categoryCodes());
            matched.addAll(match.matchedKeywords());
        }
        if (codes.isEmpty()) {
            return new Decision(ClassificationStatus.NEEDS_REVIEW, List.of(), UNRECOGNIZED, List.of(),
                    "REVIEW:" + UNRECOGNIZED, SOURCE, List.of());
        }
        List<String> categoryCodes = List.copyOf(codes);
        return new Decision(ClassificationStatus.AUTO_CLASSIFIED, categoryCodes, null, List.of(),
                autoRuleId(categoryCodes), SOURCE, List.copyOf(matched));
    }
}
