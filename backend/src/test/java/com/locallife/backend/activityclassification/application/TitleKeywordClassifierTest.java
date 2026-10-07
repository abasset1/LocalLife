package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Décision du fallback titre (issue #45) : toutes les catégories des mots-clés reconnus sont retenues,
 * sans principale ni secondaire, sans score ; plusieurs catégories ne sont jamais un conflit ; rien →
 * NO_SIGNAL.
 */
class TitleKeywordClassifierTest {

    private static final List<KeywordEntry> ENTRIES = List.of(
            new KeywordEntry("CULTURE", "concert"),
            new KeywordEntry("CULTURE", "exposition"),
            new KeywordEntry("CULTURE", "peinture"),
            new KeywordEntry("CULTURE", "théâtre"),
            new KeywordEntry("GASTRONOMY", "dégustation"),
            new KeywordEntry("MARKETS", "marché"),
            new KeywordEntry("MARKETS", "vide grenier"),
            new KeywordEntry("MARKETS", "brocante"),
            new KeywordEntry("NATURE", "nature"),
            new KeywordEntry("SPORT", "vélo"));

    @Test
    void oneKeywordIsAutoClassifiedWithItsTrace() {
        Decision decision = TitleKeywordClassifier.classify("Grand marché de Noël", ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("MARKETS"), decision.categoryCodes());
        assertEquals("TITLE_KEYWORD", decision.source());
        assertEquals(List.of("marché"), decision.matchedKeywords());
        assertEquals("TITLE_KEYWORD:AUTO:MARKETS", decision.ruleId());
    }

    @Test
    void severalKeywordsOfTheSameCategoryGiveThatCategoryOnce() {
        Decision decision = TitleKeywordClassifier.classify("Marché, brocante et vide-grenier", ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("MARKETS"), decision.categoryCodes());
        assertEquals(List.of("marché", "vide grenier", "brocante"), decision.matchedKeywords());
    }

    @Test
    void ticketExamplesKeepAllDetectedCategories() {
        assertCategories("Marché gourmand avec dégustation et concert", "MARKETS", "GASTRONOMY", "CULTURE");
        assertCategories("Balade nature à vélo", "NATURE", "SPORT");
        assertCategories("Exposition de peinture", "CULTURE");
    }

    @Test
    void severalCategoriesAreNeverAConflict() {
        Decision decision = TitleKeywordClassifier.classify("Concert au marché de Noël", ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertNull(decision.reason());
        assertEquals(List.of(), decision.candidateCodes());
        // Ordre du référentiel (stable), pas un classement.
        assertEquals(List.of("CULTURE", "MARKETS"), decision.categoryCodes());
        assertEquals(List.of("concert", "marché"), decision.matchedKeywords());
        assertEquals("TITLE_KEYWORD:AUTO:CULTURE+MARKETS", decision.ruleId());
    }

    @Test
    void theSetOfCategoriesDoesNotDependOnTheOrderOfTheReferential() {
        Decision decision = TitleKeywordClassifier.classify("Dégustation au marché", ENTRIES.reversed());

        assertEquals(Set.of("MARKETS", "GASTRONOMY"), Set.copyOf(decision.categoryCodes()));
    }

    @Test
    void noMatchIsUnclassifiedNoSignal() {
        Decision decision = TitleKeywordClassifier.classify("Atelier de couture", ENTRIES);

        assertEquals(ClassificationStatus.UNCLASSIFIED, decision.status());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", decision.ruleId());
        assertEquals(List.of(), decision.categoryCodes());
        assertNull(decision.source());
    }

    @Test
    void marcheNordiqueDoesNotMatchMarche() {
        Decision decision = TitleKeywordClassifier.classify("Marche nordique", ENTRIES);

        assertEquals(ClassificationStatus.UNCLASSIFIED, decision.status());
    }

    @Test
    void artDoesNotMatchArtisanat() {
        List<KeywordEntry> entries = List.of(new KeywordEntry("CULTURE", "art"));

        assertEquals(ClassificationStatus.UNCLASSIFIED,
                TitleKeywordClassifier.classify("Salon de l'artisanat", entries).status());
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED,
                TitleKeywordClassifier.classify("Atelier d'art", entries).status());
    }

    @Test
    void videGrenierMatchesAHyphenatedTitle() {
        Decision decision = TitleKeywordClassifier.classify("Grand vide-grenier municipal", ENTRIES);

        assertEquals(List.of("MARKETS"), decision.categoryCodes());
    }

    @Test
    void nullBlankTitleOrNoEntriesIsNoSignal() {
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify(null, ENTRIES).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("   ", ENTRIES).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("marché", List.of()).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("marché", null).ruleId());
    }

    private static void assertCategories(String title, String... expected) {
        Decision decision = TitleKeywordClassifier.classify(title, ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status(), title);
        assertEquals(Set.of(expected), Set.copyOf(decision.categoryCodes()), title);
        assertEquals(expected.length, decision.categoryCodes().size(), title);
    }
}
