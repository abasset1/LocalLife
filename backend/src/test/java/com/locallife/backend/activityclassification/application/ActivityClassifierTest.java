package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** CAT-008 — décision de classification centralisée. */
class ActivityClassifierTest {

    private static final Set<String> V1_CODES = Set.of(
            "CULTURE", "GASTRONOMY", "MARKETS", "COMMERCE", "SPORT",
            "LEISURE", "NATURE", "TOURISM", "CITIZEN");

    @Test
    void noSignalIsUnclassified() {
        Decision decision = ActivityClassifier.classify(null, List.of());

        assertEquals(ClassificationStatus.UNCLASSIFIED, decision.status());
        assertNull(decision.categoryCode());
        assertEquals("UNCLASSIFIED:NO_SIGNAL", decision.ruleId());
    }

    @Test
    void blankSignalsAreNoSignal() {
        assertEquals(ClassificationStatus.UNCLASSIFIED,
                ActivityClassifier.classify("  ", java.util.Arrays.asList(" ", null)).status());
    }

    @Test
    void singleUnambiguousCategoryIsAutoClassified() {
        Decision decision = ActivityClassifier.classify("Théâtre", List.of());

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("CULTURE", decision.categoryCode());
        assertEquals("AUTO:CULTURE", decision.ruleId());
    }

    @Test
    void categoryComesFromTagsWhenSourceCategoryIsMissing() {
        Decision decision = ActivityClassifier.classify(null, List.of("noël", "brocante"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("MARKETS", decision.categoryCode());
    }

    @Test
    void agreeingSignalsAreStillAutoClassified() {
        Decision decision = ActivityClassifier.classify("concert", List.of("musique", "jazz"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("CULTURE", decision.categoryCode());
    }

    @Test
    void audienceAndTypeSignalsNeverDecideOrBlockTheCategory() {
        Decision decision = ActivityClassifier.classify("Atelier", List.of("jeune public", "théâtre"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("CULTURE", decision.categoryCode());
    }

    @Test
    void conflictingCategoriesNeedReviewWithBothCandidates() {
        Decision decision = ActivityClassifier.classify("concert", List.of("sport"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals(ActivityClassifier.CONFLICTING_SIGNALS, decision.reason());
        assertNull(decision.categoryCode());
        assertEquals(List.of("CULTURE", "SPORT"), decision.candidateCodes());
    }

    @Test
    void heritageAloneIsAmbiguousBetweenCultureAndTourism() {
        Decision decision = ActivityClassifier.classify("patrimoine", List.of("visite guidée"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals("AMBIGUOUS_CATEGORY", decision.reason());
        assertNull(decision.categoryCode());
        assertEquals(List.of("CULTURE", "TOURISM"), decision.candidateCodes());
    }

    @Test
    void ambiguousSignalCompatibleWithTheMappedCategoryDoesNotBlockIt() {
        // « exposition » → CULTURE ; « patrimoine » → CULTURE ou TOURISM : cohérent avec CULTURE.
        Decision decision = ActivityClassifier.classify("exposition", List.of("patrimoine"));

        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, decision.status());
        assertEquals("CULTURE", decision.categoryCode());
    }

    @Test
    void ambiguousSignalContradictingTheMappedCategoryNeedsReview() {
        // « marché » → MARKETS ; « patrimoine » → CULTURE ou TOURISM : MARKETS n'y figure pas.
        Decision decision = ActivityClassifier.classify("marché", List.of("patrimoine"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals(ActivityClassifier.CONFLICTING_SIGNALS, decision.reason());
        assertTrue(decision.candidateCodes().containsAll(List.of("MARKETS", "CULTURE", "TOURISM")));
    }

    @Test
    void employmentDomainIsNeverClassifiedEvenWithAnotherClassifiableSignal() {
        Decision decision = ActivityClassifier.classify("Recrutement", List.of("théâtre"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals("EMPLOYMENT_DOMAIN", decision.reason());
        assertNull(decision.categoryCode());
    }

    @Test
    void unrecognizedSignalsNeedReviewNotUnclassified() {
        Decision decision = ActivityClassifier.classify("Cameo Commanderie", List.of("NDMBX26"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals("UNRECOGNIZED", decision.reason());
        assertNull(decision.categoryCode());
    }

    @Test
    void onlyAudienceSignalNeedsReviewAndCreatesNoCategory() {
        Decision decision = ActivityClassifier.classify("Jeune public", List.of("famille"));

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertEquals("AUDIENCE_VALUE", decision.reason());
        assertNull(decision.categoryCode());
    }

    @Test
    void onlyEverProducesExistingV1CodesAndNeverCreatesOne() {
        List<String> values = List.of(
                "Théâtre", "marché", "sport", "nature", "food truck", "jeux", "Medieval", "Festival médiéval",
                "Recrutement", "patrimoine", "MEDIEVAL");
        for (String value : values) {
            Decision decision = ActivityClassifier.classify(value, List.of(value));
            if (decision.categoryCode() != null) {
                assertTrue(V1_CODES.contains(decision.categoryCode()), decision.categoryCode());
            }
            assertTrue(decision.candidateCodes().stream().allMatch(V1_CODES::contains), value);
        }
    }

    /** Exemple du sprint : « Festival médiéval » ne peut pas créer une catégorie MEDIEVAL. */
    @Test
    void medievalFestivalDoesNotCreateAMedievalCategory() {
        Decision decision = ActivityClassifier.classify("Festival médiéval", List.of());

        assertEquals(ClassificationStatus.NEEDS_REVIEW, decision.status());
        assertNull(decision.categoryCode());
    }

    @Test
    void onlyAutoClassifiedHasACategoryCode() {
        for (String value : List.of("Théâtre", "patrimoine", "Recrutement", "Jeune public", "xyz")) {
            Decision decision = ActivityClassifier.classify(value, List.of());
            assertEquals(decision.status() == ClassificationStatus.AUTO_CLASSIFIED,
                    decision.categoryCode() != null, value);
        }
    }

    @Test
    void isDeterministic() {
        assertEquals(
                ActivityClassifier.classify("concert", List.of("sport")),
                ActivityClassifier.classify("concert", List.of("sport")));
    }
}
