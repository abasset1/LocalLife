package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Règle unique de correspondance texte → catégories (issue #50), appliquée à l'identique au titre et à
 * chaque catégorie d'origine / tag de la source. Comparaison : {@link KeywordMatcher} (casse, espaces et
 * séparateurs ignorés, accents conservés, mots entiers).
 *
 * <ol>
 *   <li>si le texte <b>entier</b> est exactement un mot-clé, seuls ce(s) mot(s)-clé(s) décident
 *       (« Marché du travail » → EMPLOYMENT, pas MARKETS par « marché ») ;</li>
 *   <li>sinon, <b>toutes</b> les catégories des mots-clés reconnus dans le texte (modèle 1..N, issue #45).</li>
 * </ol>
 *
 * Ordre stable : celui des entrées (référentiel trié par id). Classe pure, sans état, déterministe.
 */
final class KeywordCategoryMatcher {

    /**
     * @param categoryCodes   catégories reconnues, sans doublon (vide si aucune)
     * @param matchedKeywords mots-clés reconnus, regroupés par catégorie, sans doublon
     */
    record Match(List<String> categoryCodes, List<String> matchedKeywords) {

        boolean isEmpty() {
            return categoryCodes.isEmpty();
        }
    }

    private KeywordCategoryMatcher() {
    }

    static Match match(String text, List<KeywordEntry> entries) {
        if (text == null || text.isBlank() || entries == null || entries.isEmpty()) {
            return new Match(List.of(), List.of());
        }
        List<KeywordEntry> whole = entries.stream()
                .filter(entry -> KeywordMatcher.matchesWhole(entry.keyword(), text))
                .toList();
        List<KeywordEntry> retained = !whole.isEmpty() ? whole : entries.stream()
                .filter(entry -> KeywordMatcher.matches(entry.keyword(), text))
                .toList();
        Map<String, List<String>> keywordsByCategory = new LinkedHashMap<>();
        for (KeywordEntry entry : retained) {
            List<String> keywords = keywordsByCategory.computeIfAbsent(entry.categoryCode(), code -> new ArrayList<>());
            if (!keywords.contains(entry.keyword())) {
                keywords.add(entry.keyword());
            }
        }
        return new Match(
                List.copyOf(keywordsByCategory.keySet()),
                keywordsByCategory.values().stream().flatMap(List::stream).toList());
    }
}
