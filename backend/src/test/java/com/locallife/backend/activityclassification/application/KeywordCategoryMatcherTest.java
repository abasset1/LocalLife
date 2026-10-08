package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Règle unique de correspondance (issue #50) : valeur entière prioritaire, sinon tous les mots-clés reconnus. */
class KeywordCategoryMatcherTest {

    private static final List<KeywordEntry> ENTRIES = List.of(
            new KeywordEntry("MARKETS", "marché"),
            new KeywordEntry("EMPLOYMENT", "marché du travail"),
            new KeywordEntry("MARKETS", "bourse aux livres"),
            new KeywordEntry("CULTURE", "livres"));

    @Test
    void wholeKeywordWinsOverTheKeywordsItContains() {
        KeywordCategoryMatcher.Match match = KeywordCategoryMatcher.match("Marché du travail", ENTRIES);

        assertEquals(List.of("EMPLOYMENT"), match.categoryCodes());
        assertEquals(List.of("marché du travail"), match.matchedKeywords());
        assertEquals(List.of("MARKETS"), KeywordCategoryMatcher.match("Bourse aux livres", ENTRIES).categoryCodes());
    }

    @Test
    void otherwiseEveryRecognisedKeywordCounts() {
        KeywordCategoryMatcher.Match match =
                KeywordCategoryMatcher.match("Forum du marché du travail et bourse aux livres", ENTRIES);

        assertEquals(List.of("MARKETS", "EMPLOYMENT", "CULTURE"), match.categoryCodes());
        assertEquals(List.of("marché", "bourse aux livres", "marché du travail", "livres"), match.matchedKeywords());
    }

    @Test
    void wholeValueComparisonIgnoresCaseAndSeparatorsButKeepsAccents() {
        assertTrue(KeywordMatcher.matchesWhole("marché du travail", "  MARCHÉ-du travail "));
        assertFalse(KeywordMatcher.matchesWhole("marché du travail", "marche du travail"));
        assertFalse(KeywordMatcher.matchesWhole("marché", "Grand marché"));
        assertFalse(KeywordMatcher.matchesWhole("", ""));
    }

    @Test
    void nothingToMatch() {
        assertTrue(KeywordCategoryMatcher.match(null, ENTRIES).isEmpty());
        assertTrue(KeywordCategoryMatcher.match("  ", ENTRIES).isEmpty());
        assertTrue(KeywordCategoryMatcher.match("marché", List.of()).isEmpty());
        assertTrue(KeywordCategoryMatcher.match("marché", null).isEmpty());
        assertTrue(KeywordCategoryMatcher.match("Marche nordique", ENTRIES).isEmpty());
    }
}
