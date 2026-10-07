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
 * <p>Décision (issue #45 : 1..N catégories, sans principale ni secondaire, sans score) :
 * <ol>
 *   <li>aucun mot-clé reconnu → {@code UNCLASSIFIED} ({@code UNCLASSIFIED:NO_SIGNAL}) ;</li>
 *   <li>un ou plusieurs mots-clés reconnus → {@code AUTO_CLASSIFIED} avec <b>toutes</b> les catégories
 *       de ces mots-clés, dans l'ordre du référentiel. Plusieurs catégories ne sont jamais un conflit.</li>
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
            return new Decision(
                    ClassificationStatus.UNCLASSIFIED, List.of(), null, List.of(), "UNCLASSIFIED:NO_SIGNAL");
        }
        List<String> matched = matchedByCategory.values().stream().flatMap(List::stream).toList();
        List<String> codes = List.copyOf(matchedByCategory.keySet());
        return new Decision(ClassificationStatus.AUTO_CLASSIFIED, codes, null, List.of(),
                SOURCE + ":" + ActivityClassifier.autoRuleId(codes), SOURCE, matched);
    }
}
