package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Règles de TICKET_CLASSIFICATION_KEYWORD_MATCHING.md : accents conservés, frontières de mots. */
class KeywordMatcherTest {

    /** Cas bloquant : « marché » ne doit jamais devenir « marche ». */
    @Test
    void marcheWithAccentDoesNotMatchMarcheWithout() {
        assertFalse(KeywordMatcher.matches("marché", "Marche nordique"));
    }

    @Test
    void marcheWithoutAccentDoesNotMatchMarcheWithAccent() {
        assertFalse(KeywordMatcher.matches("marche", "marché de Noël"));
        assertTrue(KeywordMatcher.matches("marche", "marche nordique"));
    }

    @ParameterizedTest
    @CsvSource({"théâtre,Soirée theatre", "pêche,Concours de peche", "forêt,Balade en foret", "marché,Grand marche"})
    void accentsAreNeverRemoved(String keyword, String title) {
        assertFalse(KeywordMatcher.matches(keyword, title));
    }

    @Test
    void accentedKeywordMatchesInsideAnAccentedTitle() {
        assertTrue(KeywordMatcher.matches("marché", "Grand marché de Noël"));
    }

    @Test
    void caseIsIgnored() {
        assertTrue(KeywordMatcher.matches("marché", "MARCHÉ DE NOËL"));
        assertTrue(KeywordMatcher.matches("MARCHÉ", "marché de noël"));
        assertTrue(KeywordMatcher.matches("Marché", "Le marché"));
    }

    @Test
    void decomposedUnicodeIsEquivalentToComposedButNotToUnaccented() {
        String decomposed = "marché de Noël";
        assertTrue(KeywordMatcher.matches("marché", decomposed));
        assertFalse(KeywordMatcher.matches("marche", decomposed));
    }

    @Test
    void leadingTrailingAndMultipleSpacesAreIgnored() {
        assertTrue(KeywordMatcher.matches("  vide   grenier ", "  Grand   vide \t grenier  "));
        assertTrue(KeywordMatcher.matches("marché", "   marché   "));
    }

    @Test
    void wholeWordsOnlyNeverASubstring() {
        assertFalse(KeywordMatcher.matches("art", "artisanat"));
        assertFalse(KeywordMatcher.matches("art", "Fête de l'artisan"));
        assertFalse(KeywordMatcher.matches("marché", "supermarché"));
        assertTrue(KeywordMatcher.matches("art", "atelier d'art"));
        assertTrue(KeywordMatcher.matches("art", "Art contemporain"));
    }

    @Test
    void hyphenAndSpaceAreEquivalentSeparators() {
        assertTrue(KeywordMatcher.matches("vide grenier", "Grand vide-grenier municipal"));
        assertTrue(KeywordMatcher.matches("vide-grenier", "Grand vide grenier municipal"));
        assertFalse(KeywordMatcher.matches("vide grenier", "Grand videgrenier municipal"));
    }

    @Test
    void typographicApostrophesAreEquivalent() {
        assertTrue(KeywordMatcher.matches("d'art", "Atelier d’art"));
        assertTrue(KeywordMatcher.matches("d’art", "Atelier d'art"));
        assertTrue(KeywordMatcher.matches("d'art", "Atelier dʼart"));
    }

    @Test
    void anApostropheIsNotDeleted() {
        assertFalse(KeywordMatcher.matches("d'art", "Atelier dart"));
        assertFalse(KeywordMatcher.matches("dart", "Atelier d'art"));
    }

    @Test
    void multiWordKeywordNeedsConsecutiveWordsInOrder() {
        assertTrue(KeywordMatcher.matches("sortie nature", "Grande sortie nature en famille"));
        assertFalse(KeywordMatcher.matches("sortie nature", "Sortie en pleine nature"));
        assertFalse(KeywordMatcher.matches("sortie nature", "Nature et sortie"));
    }

    @Test
    void punctuationActsAsSeparator() {
        assertTrue(KeywordMatcher.matches("marché", "Noël : marché, concerts !"));
    }

    @Test
    void digitsAreWordCharacters() {
        assertTrue(KeywordMatcher.matches("5k", "Course 5k du port"));
        assertFalse(KeywordMatcher.matches("5", "Course 5k du port"));
    }

    @Test
    void nullBlankOrSeparatorOnlyInputsNeverMatch() {
        assertFalse(KeywordMatcher.matches(null, "marché"));
        assertFalse(KeywordMatcher.matches("marché", null));
        assertFalse(KeywordMatcher.matches("  ", "marché"));
        assertFalse(KeywordMatcher.matches("-", "marché - noël"));
        assertFalse(KeywordMatcher.matches("marché", ""));
    }
}
