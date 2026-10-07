package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Décision du fallback titre : même catégorie → auto ; plusieurs catégories → principale (plus forte
 * priorité) + secondaires ; égalité de priorité au sommet → relecture ; rien → NO_SIGNAL.
 */
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
    void keywordsOfDifferentCategoriesWithTheSamePriorityNeedReviewWithoutChoosing() {
        Decision decision = TitleKeywordClassifier.classify("Concert au marché de Noël", ENTRIES);

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertNull(decision.categoryCode());
        assertEquals(ActivityClassifier.AMBIGUOUS_PRIMARY, decision.reason());
        assertEquals(List.of("MARKETS", "CULTURE"), decision.candidateCodes());
        assertEquals(List.of("marché", "concert"), decision.matchedKeywords());
        assertEquals(List.of(), decision.secondaryCategoryCodes());
        assertEquals("TITLE_KEYWORD:REVIEW:AMBIGUOUS_PRIMARY", decision.ruleId());
    }

    /** Référentiel avec priorités (100 = désigne l'événement, 50 = thématique), comme V41. */
    private static final List<KeywordEntry> PRIORITIZED = List.of(
            new KeywordEntry("CULTURE", "exposition", 100),
            new KeywordEntry("CULTURE", "concert", 100),
            new KeywordEntry("CULTURE", "architectural", 100),
            new KeywordEntry("MARKETS", "marché", 100),
            new KeywordEntry("NATURE", "sortie nature", 100),
            new KeywordEntry("GASTRONOMY", "dégustation", 50),
            new KeywordEntry("NATURE", "biodiversité", 50),
            new KeywordEntry("SPORT", "vélo", 50),
            new KeywordEntry("SPORT", "randonnée", 50));

    private static void assertPrimaryAndSecondaries(String title, String primary, List<String> secondaries) {
        Decision decision = TitleKeywordClassifier.classify(title, PRIORITIZED);

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status(), title);
        assertEquals(primary, decision.categoryCode(), title);
        assertEquals(secondaries, decision.secondaryCategoryCodes(), title);
        assertEquals("TITLE_KEYWORD:AUTO:" + primary, decision.ruleId(), title);
        assertNull(decision.reason(), title);
    }

    @Test
    void ticketExamplesGiveAPrimaryAndSecondaryCategories() {
        assertPrimaryAndSecondaries("Exposition sur la biodiversité", "CULTURE", List.of("NATURE"));
        assertPrimaryAndSecondaries("Balade à vélo", "SPORT", List.of());
        assertPrimaryAndSecondaries("Marché avec dégustation", "MARKETS", List.of("GASTRONOMY"));
        assertPrimaryAndSecondaries("Concert avec dégustation", "CULTURE", List.of("GASTRONOMY"));
        assertPrimaryAndSecondaries("Conférence sur le patrimoine architectural", "CULTURE", List.of());
        assertPrimaryAndSecondaries("Sortie nature avec randonnée", "NATURE", List.of("SPORT"));
    }

    @Test
    void primaryDoesNotDependOnTheOrderOfKeywordsInTheTitleOrTheReferential() {
        List<KeywordEntry> reversed = PRIORITIZED.reversed();

        Decision decision = TitleKeywordClassifier.classify("Dégustation au marché", reversed);

        assertEquals("MARKETS", decision.categoryCode());
        assertEquals(List.of("GASTRONOMY"), decision.secondaryCategoryCodes());
    }

    @Test
    void secondariesAreOrderedByPriorityThenDetectionOrder() {
        Decision decision = TitleKeywordClassifier.classify(
                "Concert, vélo, dégustation et biodiversité au marché", PRIORITIZED);

        // CULTURE et MARKETS à 100 : égalité au sommet → relecture, même avec d'autres domaines plus faibles.
        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals(List.of("CULTURE", "MARKETS"), decision.candidateCodes());

        Decision withoutTie = TitleKeywordClassifier.classify(
                "Exposition vélo, dégustation et biodiversité", PRIORITIZED);
        assertEquals("CULTURE", withoutTie.categoryCode());
        // Toutes à 50 : ordre de détection (= ordre du référentiel).
        assertEquals(List.of("GASTRONOMY", "NATURE", "SPORT"), withoutTie.secondaryCategoryCodes());
    }

    @Test
    void severalKeywordsOfTheSameCategoryKeepTheHighestPriority() {
        List<KeywordEntry> entries = List.of(
                new KeywordEntry("SPORT", "vélo", 50),
                new KeywordEntry("SPORT", "rugby", 100),
                new KeywordEntry("CULTURE", "concert", 100));

        // SPORT = max(50, 100) = 100, à égalité avec CULTURE → relecture.
        assertEquals(ClassificationStatus.NEEDS_REVIEW,
                TitleKeywordClassifier.classify("Concert et rugby à vélo", entries).status());
        // Sans « rugby », SPORT = 50 < CULTURE.
        assertEquals("CULTURE", TitleKeywordClassifier.classify("Concert à vélo", entries).categoryCode());
    }

    @Test
    void defaultPriorityIsTheEventLevel() {
        assertEquals(CategoryKeyword.DEFAULT_PRIORITY, new KeywordEntry("CULTURE", "concert").priority());
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
