package com.locallife.backend.activityclassification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Référentiel unique (issue #50) : les catégories d'origine de la source passent par le référentiel en base
 * ({@code category_keyword}, portées ALL + SOURCE) et la même règle que le titre — plus par la liste Java
 * {@code LegacyCategoryMapper}, supprimée. Rejoué sur l'extraction réelle du 29/09/2026, sur la base migrée.
 */
@SpringBootTest
@Transactional
class SourceKeywordReplayIntegrationTest {

    @Autowired
    private ClassificationService classificationService;

    private Set<String> sourceCategoriesOf(String sourceCategory) {
        ClassificationOutcome outcome = classificationService.decide(sourceCategory, List.of());
        return outcome.classified() ? Set.copyOf(outcome.decision().categoryCodes()) : Set.of();
    }

    /**
     * Critère « sans perte » : chaque valeur que l'ancienne liste Java classait (instantané figé avant sa
     * suppression, 130 valeurs / 3 162 activités) reste classée, avec au moins la même catégorie.
     */
    @Test
    void everyValueClassifiedByTheFormerJavaListIsStillClassified() throws IOException {
        long weight = 0;
        for (String[] row : RealExtraction.readLegacySnapshot()) {
            String value = row[0];
            Set<String> expected = Set.of(row[2].split("\\+"));
            assertThat(sourceCategoriesOf(value)).as(value).containsAll(expected);
            weight += Long.parseLong(row[1]);
        }
        assertThat(weight).isEqualTo(3_162L);
    }

    /** Chiffres documentés dans CATEGORIES_CLASSIFICATION.md §13. */
    @Test
    void replayOnRealExtractionMatchesDocumentedFigures() throws IOException {
        Map<String, Long> byOutcome = new TreeMap<>();
        Map<String, Long> byCategory = new TreeMap<>();
        for (String[] row : RealExtraction.readExtraction()) {
            long nb = Long.parseLong(row[1]);
            ClassificationOutcome outcome = classificationService.decide(row[0], List.of());
            ClassificationStatus status = outcome.classified() ? ClassificationStatus.AUTO_CLASSIFIED
                    : outcome.decision().status();
            byOutcome.merge(status.name(), nb, Long::sum);
            outcome.decision().categoryCodes().forEach(code -> byCategory.merge(code, nb, Long::sum));
        }
        System.out.println("Rejeu catégories d'origine : " + byOutcome + " ; par catégorie : " + byCategory);

        assertThat(byOutcome.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(47_338L);
        assertThat(byOutcome.get("UNCLASSIFIED")).isEqualTo(41_696L);
        assertThat(byOutcome.get("AUTO_CLASSIFIED")).isEqualTo(3_790L);
        assertThat(byOutcome.get("NEEDS_REVIEW")).isEqualTo(1_852L);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        // Valeur entière = mot-clé : elle seule décide (pas MARKETS par « marché »).
        "Marché du travail;EMPLOYMENT",
        "Métiers d'art;CULTURE",
        "Journées européennes des métiers d'art;CULTURE",
        // Mots-clés de portée SOURCE : fiables dans les mots-clés d'un agenda.
        "Métiers du transport et de la logistique;EMPLOYMENT",
        "Formation;EMPLOYMENT",
        "S'informer;EMPLOYMENT",
        "culture;CULTURE",
        // Même règle que le titre : mots entiers, plusieurs catégories possibles.
        "THÉÂTRE;CULTURE",
        "theatre de papier;CULTURE",
        "Journées du Patrimoine;CULTURE,TOURISM",
        "Métiers de la culture et du spectacle;EMPLOYMENT,CULTURE"
    })
    void sourceValuesFollowTheUnifiedRule(String value, String codes) {
        assertThat(sourceCategoriesOf(value)).isEqualTo(Set.of(codes.split(",")));
    }

    @Test
    void sourceScopeKeywordsAreNeverAppliedToTitles() {
        // « formation », « métiers », « travail » sont SOURCE : un titre libre ne les utilise pas.
        for (String title : List.of("Formation aux premiers secours", "Travail du bois", "Métiers de bouche")) {
            ClassificationOutcome outcome = classificationService.decide(title, null, null);
            assertThat(outcome.decision().categoryCodes()).as(title).doesNotContain("EMPLOYMENT");
        }
    }

    @Test
    void valuesWithoutAnyKeywordNeedReviewAndAreNeverGuessed() {
        for (String value : List.of("Jeune public", "Atelier", "Visite", "Cameo Commanderie", "Festival médiéval")) {
            ClassificationOutcome outcome = classificationService.decide(value, List.of());
            assertThat(outcome.decision().status()).as(value).isEqualTo(ClassificationStatus.NEEDS_REVIEW);
            assertThat(outcome.decision().reason()).as(value).isEqualTo(ActivityClassifier.UNRECOGNIZED);
            assertThat(outcome.decision().categoryCodes()).as(value).isEmpty();
        }
    }
}
