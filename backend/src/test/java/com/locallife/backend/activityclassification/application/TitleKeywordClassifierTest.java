package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Décision du fallback titre : même catégorie → auto, catégories différentes → relecture, rien → NO_SIGNAL. */
class TitleKeywordClassifierTest {

    private static final List<KeywordEntry> ENTRIES = List.of(
            new KeywordEntry("MARKETS", "marché"),
            new KeywordEntry("MARKETS", "vide grenier"),
            new KeywordEntry("MARKETS", "brocante"),
            new KeywordEntry("CULTURE", "concert"),
            new KeywordEntry("CULTURE", "théâtre"),
            new KeywordEntry("GASTRONOMY", "dégustation"));

    @Test
    void oneKeywordIsAutoClassifiedWithItsTrace() {
        Decision decision = TitleKeywordClassifier.classify("Grand marché de Noël", ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("MARKETS", decision.categoryCode());
        assertEquals("TITLE_KEYWORD", decision.source());
        assertEquals(List.of("marché"), decision.matchedKeywords());
        assertEquals("TITLE_KEYWORD:AUTO:MARKETS", decision.ruleId());
    }

    @Test
    void severalKeywordsOfTheSameCategoryAreAutoClassified() {
        Decision decision = TitleKeywordClassifier.classify("Marché, brocante et vide-grenier", ENTRIES);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("MARKETS", decision.categoryCode());
        assertEquals(List.of("marché", "vide grenier", "brocante"), decision.matchedKeywords());
    }

    @Test
    void keywordsOfDifferentCategoriesNeedReviewWithoutChoosing() {
        Decision decision = TitleKeywordClassifier.classify("Concert au marché de Noël", ENTRIES);

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertNull(decision.categoryCode());
        assertEquals(ActivityClassifier.CONFLICTING_SIGNALS, decision.reason());
        assertEquals(List.of("MARKETS", "CULTURE"), decision.candidateCodes());
        assertEquals(List.of("marché", "concert"), decision.matchedKeywords());
        assertEquals("TITLE_KEYWORD:REVIEW:CONFLICTING_SIGNALS", decision.ruleId());
    }

    @Test
    void entryOrderDoesNotChangeTheOutcomeOfAConflict() {
        List<KeywordEntry> reversed = List.of(ENTRIES.get(3), ENTRIES.get(0));

        Decision decision = TitleKeywordClassifier.classify("Concert au marché", reversed);

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertNull(decision.categoryCode());
    }

    @Test
    void noMatchIsUnclassifiedNoSignal() {
        Decision decision = TitleKeywordClassifier.classify("Atelier de peinture", ENTRIES);

        assertEquals(ClassificationStatus.UNCLASSIFIED, decision.status());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", decision.ruleId());
        assertNull(decision.categoryCode());
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

        assertEquals("MARKETS", decision.categoryCode());
    }

    @Test
    void nullBlankTitleOrNoEntriesIsNoSignal() {
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify(null, ENTRIES).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("   ", ENTRIES).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("marché", List.of()).ruleId());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", TitleKeywordClassifier.classify("marché", null).ruleId());
    }
}
