package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Outcome;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.Result;
import com.locallife.backend.activityclassification.application.LegacyCategoryMapper.ReviewReason;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * CAT-007 — tests de la correspondance ancienne valeur → catégorie V1.
 * Le test sur l'extraction réelle rend la migration reproductible : les
 * chiffres du §11 de {@code docs/CATEGORY_MIGRATION_MAP.md} sont ceux
 * vérifiés ici.
 */
class LegacyCategoryMapperTest {

    private static final Set<String> V1_CATEGORY_CODES = Set.of(
            "CULTURE", "GASTRONOMY", "MARKETS", "COMMERCE", "SPORT",
            "LEISURE", "NATURE", "TOURISM", "CITIZEN", "EMPLOYMENT");

    @ParameterizedTest
    @CsvSource({
            "sport,SPORT",
            "Sport,SPORT",
            "culture,CULTURE",
            "Théâtre,CULTURE",
            "concert,CULTURE",
            "Cinéma,CULTURE",
            "marché,MARKETS",
            "Marché,MARKETS",
            "brocante,MARKETS",
            "food truck,GASTRONOMY",
            "dégustation,GASTRONOMY",
            "nature,NATURE",
            "jeux de société,LEISURE"
    })
    void mapsUnambiguousValuesToTheirCategory(String legacy, String expectedCode) {
        Result result = LegacyCategoryMapper.map(legacy);

        assertEquals(Outcome.MAPPED, result.outcome());
        assertEquals(expectedCode, result.categoryCode());
        assertEquals("EXACT:" + expectedCode, result.ruleId());
    }

    @Test
    void nullOrBlankIsNoValueNotAnAmbiguity() {
        assertEquals(Outcome.NO_VALUE, LegacyCategoryMapper.map(null).outcome());
        assertEquals(Outcome.NO_VALUE, LegacyCategoryMapper.map("   ").outcome());
        assertNull(LegacyCategoryMapper.map(null).categoryCode());
    }

    @Test
    void matchingIsExactNeverBySubstring() {
        // « marché du travail » est du domaine emploi, jamais un marché.
        Result result = LegacyCategoryMapper.map("Marché du travail");

        assertEquals(Outcome.MAPPED, result.outcome());
        assertEquals("EMPLOYMENT", result.categoryCode());
        // « marche nordique » est de la marche à pied, pas un marché.
        assertEquals(Outcome.NEEDS_REVIEW, LegacyCategoryMapper.map("Marche nordique").outcome());
    }

    @Test
    void heritageAndVisitsAreAmbiguousBetweenCultureAndTourism() {
        Result result = LegacyCategoryMapper.map("Journées du patrimoine");

        assertEquals(Outcome.NEEDS_REVIEW, result.outcome());
        assertEquals(ReviewReason.AMBIGUOUS_CATEGORY, result.reason());
        assertEquals(List.of("CULTURE", "TOURISM"), result.candidateCategoryCodes());
        assertNull(result.categoryCode());
    }

    @Test
    void genericLeisureValueIsNotForcedIntoALeisureCategory() {
        Result result = LegacyCategoryMapper.map("loisir");

        assertEquals(Outcome.NEEDS_REVIEW, result.outcome());
        assertNull(result.categoryCode());
    }

    @Test
    void audienceAndTypeValuesAreNotCategories() {
        assertEquals(ReviewReason.AUDIENCE_VALUE, LegacyCategoryMapper.map("Jeune public").reason());
        assertEquals(ReviewReason.AUDIENCE_VALUE, LegacyCategoryMapper.map("Tout public").reason());
        assertEquals(ReviewReason.ACTIVITY_TYPE_VALUE, LegacyCategoryMapper.map("Atelier").reason());
        assertEquals(ReviewReason.ACTIVITY_TYPE_VALUE, LegacyCategoryMapper.map("Festival").reason());
    }

    @Test
    void employmentDomainIsTheEmploymentCategory() {
        // Issue #49 (option A) : jetons emploi (mots entiers) → EMPLOYMENT.
        for (String value : List.of("Recrutement", "#TousMobilisés", "Aides à l'emploi",
                "Métiers du BTP", "L'alternance", "Création d'entreprise", "Décroche un job d'été",
                "La Mer embauche")) {
            Result result = LegacyCategoryMapper.map(value);
            assertEquals(Outcome.MAPPED, result.outcome(), value);
            assertEquals("EMPLOYMENT", result.categoryCode(), value);
            assertEquals("TOKEN:EMPLOYMENT", result.ruleId(), value);
        }
        // Libellés entiers propres aux agendas emploi, trop génériques pour être des jetons.
        for (String value : List.of("S'informer", "Se préparer", "Les clés pour réussir", "Détection de potentiel",
                "Mobilisation 50 ans et plus", "1 jeune  1 solution")) {
            Result result = LegacyCategoryMapper.map(value);
            assertEquals("EMPLOYMENT", result.categoryCode(), value);
            assertEquals("EXACT:EMPLOYMENT", result.ruleId(), value);
        }
    }

    @Test
    void employmentSignalsStayStrict() {
        // Pas de sous-chaîne : « Se préparer à la retraite » n'est pas le libellé « Se préparer ».
        assertNull(LegacyCategoryMapper.map("Se préparer à la retraite").categoryCode());
        // « TaskForceEntreprise » est un seul mot : pas le jeton « entreprise ».
        assertNull(LegacyCategoryMapper.map("TaskForceEntreprise").categoryCode());
        // Métiers d'art : artisanat d'art (CULTURE), pas le domaine emploi.
        assertEquals("CULTURE", LegacyCategoryMapper.map("Métiers d'art").categoryCode());
        assertEquals("CULTURE", LegacyCategoryMapper.map("Journées européennes des métiers d'art").categoryCode());
    }

    @Test
    void venuesArtistsAndInternalCodesAreUnrecognizedNotGuessed() {
        for (String value : List.of("Cameo Commanderie", "Isabelle Lafon", "NDMBX26", "Le Star", "MSB")) {
            Result result = LegacyCategoryMapper.map(value);
            assertEquals(Outcome.NEEDS_REVIEW, result.outcome(), value);
            assertEquals(ReviewReason.UNRECOGNIZED, result.reason(), value);
            assertNull(result.categoryCode(), value);
        }
    }

    @Test
    void mappingIsDeterministic() {
        assertEquals(LegacyCategoryMapper.map("Théâtre"), LegacyCategoryMapper.map("Théâtre"));
        assertEquals(LegacyCategoryMapper.map("théâtre"), LegacyCategoryMapper.map("THEATRE"));
    }

    /**
     * Rejoue la correspondance sur l'extraction réelle du 29/09/2026
     * (47 338 activités) : les chiffres ci-dessous sont ceux du §11 de
     * {@code docs/CATEGORY_MIGRATION_MAP.md}.
     */
    @Test
    void replayOnRealExtractionMatchesDocumentedFigures() throws IOException {
        Map<String, Long> activitiesByRule = new HashMap<>();
        long total = 0;
        for (String[] row : readExtraction()) {
            Result result = LegacyCategoryMapper.map(row[0]);
            long nb = Long.parseLong(row[1]);
            total += nb;
            activitiesByRule.merge(result.ruleId(), nb, Long::sum);
            if (result.outcome() == Outcome.MAPPED) {
                assertTrue(V1_CATEGORY_CODES.contains(result.categoryCode()), result.categoryCode());
            } else {
                assertNull(result.categoryCode());
            }
        }

        assertEquals(47_338L, total);
        long mapped = activitiesByRule.entrySet().stream()
                .filter(e -> e.getKey().startsWith("EXACT:") || e.getKey().startsWith("TOKEN:"))
                .mapToLong(Map.Entry::getValue).sum();
        long needsReview = activitiesByRule.entrySet().stream()
                .filter(e -> e.getKey().startsWith("REVIEW:")).mapToLong(Map.Entry::getValue).sum();
        // Issue #49 : le domaine emploi est une catégorie (EMPLOYMENT) — avant : 854 classées, 4 788 à relire.
        assertEquals(3_162L, mapped);
        assertEquals(2_480L, needsReview);
        assertEquals(41_696L, activitiesByRule.get("NO_VALUE"));
        assertEquals(total, mapped + needsReview + activitiesByRule.get("NO_VALUE"));

        assertEquals(808L, activitiesByRule.get("EXACT:CULTURE"));
        assertEquals(1_904L, activitiesByRule.get("TOKEN:EMPLOYMENT"));
        assertEquals(404L, activitiesByRule.get("EXACT:EMPLOYMENT"));
        assertEquals(252L, activitiesByRule.get("REVIEW:" + ReviewReason.AMBIGUOUS_CATEGORY));
        assertEquals(232L, activitiesByRule.get("REVIEW:" + ReviewReason.AUDIENCE_VALUE));
        assertEquals(175L, activitiesByRule.get("REVIEW:" + ReviewReason.ACTIVITY_TYPE_VALUE));
        assertEquals(1_821L, activitiesByRule.get("REVIEW:" + ReviewReason.UNRECOGNIZED));
    }

    /** Lignes {category, nb} de l'extraction (colonne source_type ignorée). */
    static List<String[]> readExtraction() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = LegacyCategoryMapperTest.class
                .getResourceAsStream("/legacy/categorie_source_type.csv");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // en-tête
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = parseCsvLine(line);
                rows.add(new String[] {cells[1], cells[2]});
            }
        }
        return rows;
    }

    private static String[] parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }
}
