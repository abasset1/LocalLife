package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.PrimaryCategorySelector.Selection;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback de classification par le titre (docs/TICKET_TITLE_KEYWORD_CLASSIFICATION.md).
 *
 * <p>N'est appelé par {@code ClassificationService} que lorsque les keywords source sont absents :
 * il ne remplace ni ne contredit jamais un keyword source. Les mots-clés viennent de
 * {@code category_keyword} (aucun mot-clé métier dans le code) ; la comparaison est celle de
 * {@link KeywordMatcher} (accents conservés, frontières de mots).
 *
 * <p>Décision (issue #43, principal + secondaires) :
 * <ol>
 *   <li>aucun mot-clé reconnu → {@code UNCLASSIFIED} ({@code UNCLASSIFIED:NO_SIGNAL}) ;</li>
 *   <li>un ou plusieurs mots-clés de la <b>même</b> catégorie → {@code AUTO_CLASSIFIED} ;</li>
 *   <li>mots-clés de <b>plusieurs</b> catégories → la catégorie dont le mot-clé reconnu a la plus forte
 *       priorité devient principale, les autres secondaires ({@code AUTO_CLASSIFIED}) — voir
 *       {@link PrimaryCategorySelector} ;</li>
 *   <li>égalité de priorité au sommet entre plusieurs catégories → {@code NEEDS_REVIEW}
 *       ({@link ActivityClassifier#AMBIGUOUS_PRIMARY}, ex aequo listés) : aucun choix arbitraire.</li>
 * </ol>
 *
 * Classe pure, sans état ni dépendance.
 */
public final class TitleKeywordClassifier {

    /** Origine tracée dans {@code classification_record.classification_source}. */
    public static final String SOURCE = "TITLE_KEYWORD";

    /** Mot-clé du référentiel avec le code de sa catégorie et sa priorité (plus grand = plus fort). */
    public record KeywordEntry(String categoryCode, String keyword, int priority) {

        /** Mot-clé de priorité par défaut ({@link CategoryKeyword#DEFAULT_PRIORITY}). */
        public KeywordEntry(String categoryCode, String keyword) {
            this(categoryCode, keyword, CategoryKeyword.DEFAULT_PRIORITY);
        }
    }

    private TitleKeywordClassifier() {
    }

    public static Decision classify(String title, List<KeywordEntry> entries) {
        Map<String, List<String>> matchedByCategory = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        if (title != null && !title.isBlank() && entries != null) {
            for (KeywordEntry entry : entries) {
                if (KeywordMatcher.matches(entry.keyword(), title)) {
                    List<String> keywords = matchedByCategory.computeIfAbsent(entry.categoryCode(),
                            code -> new ArrayList<>());
                    if (!keywords.contains(entry.keyword())) {
                        keywords.add(entry.keyword());
                    }
                    scores.merge(entry.categoryCode(), entry.priority(), Math::max);
                }
            }
        }
        if (matchedByCategory.isEmpty()) {
            return new Decision(ClassificationStatus.UNCLASSIFIED, null, null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }
        List<String> matched = matchedByCategory.values().stream().flatMap(List::stream).toList();
        Selection selection = PrimaryCategorySelector.select(scores, null);
        if (selection.decided()) {
            String code = selection.primaryCode();
            return new Decision(ClassificationStatus.AUTO_CLASSIFIED, code, null, List.of(),
                    SOURCE + ":AUTO:" + code, SOURCE, matched, selection.secondaryCodes());
        }
        return new Decision(ClassificationStatus.NEEDS_REVIEW, null, ActivityClassifier.AMBIGUOUS_PRIMARY,
                selection.tiedCodes(), SOURCE + ":REVIEW:" + ActivityClassifier.AMBIGUOUS_PRIMARY, SOURCE, matched,
                List.of());
    }
}
