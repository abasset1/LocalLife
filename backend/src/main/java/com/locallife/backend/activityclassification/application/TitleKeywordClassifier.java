package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
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
 * <p>Décision :
 * <ol>
 *   <li>aucun mot-clé reconnu → {@code UNCLASSIFIED} ({@code UNCLASSIFIED:NO_SIGNAL}) ;</li>
 *   <li>un ou plusieurs mots-clés de la <b>même</b> catégorie → {@code AUTO_CLASSIFIED} ;</li>
 *   <li>mots-clés de <b>plusieurs</b> catégories → {@code NEEDS_REVIEW} ({@code CONFLICTING_SIGNALS},
 *       candidats listés) : aucun choix arbitraire, aucune priorité.</li>
 * </ol>
 *
 * Classe pure, sans état ni dépendance.
 */
public final class TitleKeywordClassifier {

    /** Origine tracée dans {@code classification_record.classification_source}. */
    public static final String SOURCE = "TITLE_KEYWORD";

    /** Mot-clé du référentiel avec le code de sa catégorie. */
    public record KeywordEntry(String categoryCode, String keyword) {
    }

    private TitleKeywordClassifier() {
    }

    public static Decision classify(String title, List<KeywordEntry> entries) {
        Map<String, List<String>> matchedByCategory = new LinkedHashMap<>();
        if (title != null && !title.isBlank() && entries != null) {
            for (KeywordEntry entry : entries) {
                if (KeywordMatcher.matches(entry.keyword(), title)) {
                    List<String> keywords = matchedByCategory.computeIfAbsent(entry.categoryCode(),
                            code -> new ArrayList<>());
                    if (!keywords.contains(entry.keyword())) {
                        keywords.add(entry.keyword());
                    }
                }
            }
        }
        if (matchedByCategory.isEmpty()) {
            return new Decision(ClassificationStatus.UNCLASSIFIED, null, null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }
        List<String> matched = matchedByCategory.values().stream().flatMap(List::stream).toList();
        if (matchedByCategory.size() == 1) {
            String code = matchedByCategory.keySet().iterator().next();
            return new Decision(ClassificationStatus.AUTO_CLASSIFIED, code, null, List.of(),
                    SOURCE + ":AUTO:" + code, SOURCE, matched);
        }
        return new Decision(ClassificationStatus.NEEDS_REVIEW, null, ActivityClassifier.CONFLICTING_SIGNALS,
                List.copyOf(matchedByCategory.keySet()),
                SOURCE + ":REVIEW:" + ActivityClassifier.CONFLICTING_SIGNALS, SOURCE, matched);
    }
}
