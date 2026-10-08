package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.TitleKeywordClassifier.KeywordEntry;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Décision sur les keywords source (catégorie d'origine + tags), avec le référentiel unique (issue #50) :
 * même règle que le titre ({@link KeywordCategoryMatcher}), mots-clés fournis par le référentiel — ici un
 * petit référentiel de test, les données réelles sont couvertes par {@code SourceKeywordReplayIntegrationTest}.
 */
class ActivityClassifierTest {

    private static final List<KeywordEntry> ENTRIES = List.of(
            new KeywordEntry("CULTURE", "théâtre"),
            new KeywordEntry("CULTURE", "theatre"),
            new KeywordEntry("CULTURE", "concert"),
            new KeywordEntry("CULTURE", "métiers d'art"),
            new KeywordEntry("MARKETS", "marché"),
            new KeywordEntry("MARKETS", "brocante"),
            new KeywordEntry("SPORT", "sport"),
            new KeywordEntry("CULTURE", "patrimoine"),
            new KeywordEntry("TOURISM", "patrimoine"),
            new KeywordEntry("EMPLOYMENT", "recrutement"),
            new KeywordEntry("EMPLOYMENT", "métiers"),
            new KeywordEntry("EMPLOYMENT", "marché du travail"));

    private static Decision classify(String sourceCategory, List<String> tags) {
        return ActivityClassifier.classify(sourceCategory, tags, ENTRIES);
    }

    @Test
    void noSignalIsUnclassified() {
        Decision decision = classify(null, List.of());

        assertEquals(ClassificationStatus.UNCLASSIFIED, decision.status());
        assertEquals(List.of(), decision.categoryCodes());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", decision.ruleId());
        assertNull(decision.source());
    }

    @Test
    void blankSignalsAreNoSignal() {
        assertEquals(ClassificationStatus.UNCLASSIFIED, classify("  ", Arrays.asList(" ", null)).status());
    }

    @Test
    void sourceCategoryMatchingAKeywordIsAutoClassifiedWithItsTrace() {
        Decision decision = classify("Théâtre", List.of());

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("CULTURE"), decision.categoryCodes());
        assertEquals("AUTO:CULTURE", decision.ruleId());
        assertEquals(ActivityClassifier.SOURCE, decision.source());
        assertEquals(List.of("théâtre"), decision.matchedKeywords());
    }

    @Test
    void sameRuleAsTheTitle_CaseSeparatorsAndWholeWords() {
        assertEquals(List.of("CULTURE"), classify("THÉÂTRE", List.of()).categoryCodes());
        assertEquals(List.of("CULTURE"), classify("theatre de papier", List.of()).categoryCodes());
        // Accents conservés, comme pour le titre : « marche » n'est pas « marché ».
        assertEquals(ClassificationStatus.NEEDS_REVIEW, classify("Marche nordique", List.of()).status());
    }

    @Test
    void categoryComesFromTagsWhenSourceCategoryIsMissing() {
        Decision decision = classify(null, List.of("noël", "brocante"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("MARKETS"), decision.categoryCodes());
    }

    @Test
    void allCategoriesOfAllSignalsAreKept_InSignalOrder() {
        Decision decision = classify("concert", List.of("sport", "marché", "concert"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertNull(decision.reason());
        assertEquals(List.of("CULTURE", "SPORT", "MARKETS"), decision.categoryCodes());
        assertEquals("AUTO:CULTURE+SPORT+MARKETS", decision.ruleId());
    }

    @Test
    void oneKeywordCanMeanSeveralCategories() {
        assertEquals(List.of("CULTURE", "TOURISM"), classify("Journées du patrimoine", List.of()).categoryCodes());
    }

    @Test
    void aValueThatIsExactlyAKeywordIsDecidedByItAlone() {
        // « Marché du travail » est un mot-clé EMPLOYMENT : « marché » (MARKETS) ne s'y ajoute pas.
        assertEquals(List.of("EMPLOYMENT"), classify("Marché du travail", List.of()).categoryCodes());
        // « Métiers d'art » est un mot-clé CULTURE : « métiers » (EMPLOYMENT) ne s'y ajoute pas.
        assertEquals(List.of("CULTURE"), classify("Métiers d'art", List.of()).categoryCodes());
        // Sinon tous les mots-clés reconnus comptent.
        assertEquals(List.of("CULTURE", "EMPLOYMENT"), classify("Métiers du théâtre", List.of()).categoryCodes());
    }

    @Test
    void employmentIsACategoryCombinedWithTheOthers() {
        Decision decision = classify("Recrutement", List.of("théâtre"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("EMPLOYMENT", "CULTURE"), decision.categoryCodes());
    }

    @Test
    void signalsWithoutAnyKeywordNeedReviewAndAreNeverGuessed() {
        for (String value : List.of("Jeune public", "Atelier", "Cameo Commanderie", "Festival médiéval")) {
            Decision decision = classify(value, List.of(value));

            assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status(), value);
            assertEquals(ActivityClassifier.UNRECOGNIZED, decision.reason(), value);
            assertEquals("REVIEW:UNRECOGNIZED", decision.ruleId(), value);
            assertEquals(List.of(), decision.categoryCodes(), value);
        }
    }

    @Test
    void anUnknownSignalDoesNotBlockTheRecognisedOnes() {
        Decision decision = classify("Cameo Commanderie", List.of("Jeune public", "concert"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals(List.of("CULTURE"), decision.categoryCodes());
    }

    @Test
    void noReferentialMeansNothingIsRecognised() {
        assertEquals(
                ClassificationStatus.NEEDS_REVIEW,
                ActivityClassifier.classify("Théâtre", List.of(), List.of()).status());
        assertEquals(
                ClassificationStatus.NEEDS_REVIEW,
                ActivityClassifier.classify("Théâtre", List.of(), null).status());
    }

    @Test
    void isDeterministic() {
        assertEquals(classify("concert", List.of("sport")), classify("concert", List.of("sport")));
    }
}
