package com.locallife.backend.activityclassification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit du référentiel catégories + mots-clés (issue #47, docs/AUDIT_REFERENTIEL_CATEGORIES.md), sur la
 * base réelle migrée (V39 à V43) : couverture de chaque catégorie, absence de mots de format ou de public,
 * titres témoins, faux positifs à éviter, et couverture du vocabulaire réel de l'extraction OpenAgenda du
 * 29/09/2026 ({@code legacy/categorie_source_type.csv}).
 *
 * <p>Les titres passent par le chemin réel de classification ({@link ClassificationService#decide} sans
 * keyword source, donc le fallback titre) : le moteur n'est pas modifié, seules les données le sont.
 */
@SpringBootTest
@Transactional
class KeywordReferentialAuditIntegrationTest {

    /**
     * Couverture minimale de tout le vocabulaire réel, emploi compris depuis la catégorie EMPLOYMENT
     * (issue #49, V44) : mesurée à 50,8 % (27,3 % avec V43 seul). Garde-fou contre une régression du
     * référentiel, pas un objectif.
     */
    private static final double MIN_COVERAGE = 0.50;

    @Autowired
    private ClassificationService classificationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Set<String> categoriesOf(String title) {
        ClassificationOutcome outcome = classificationService.decide(title, null, null);
        return outcome.classified() ? Set.copyOf(outcome.decision().categoryCodes()) : Set.of();
    }

    @Test
    void everyActiveCategoryIsCoveredByKeywords() {
        Map<String, Integer> activeKeywordsByCategory = jdbcTemplate.query(
                "SELECT c.code, count(k.id) FILTER (WHERE k.active) AS n FROM category c "
                        + "LEFT JOIN category_keyword k ON k.category_id = c.id WHERE c.active GROUP BY c.code",
                rs -> {
                    Map<String, Integer> counts = new java.util.HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("code"), rs.getInt("n"));
                    }
                    return counts;
                });

        assertThat(activeKeywordsByCategory.keySet())
                .containsExactlyInAnyOrderElementsOf(categoryRepository.findAll().stream()
                        .filter(Category::active).map(Category::code).toList());
        assertThat(activeKeywordsByCategory).allSatisfy((code, count) ->
                assertThat(count).as("mots-clés actifs de %s", code).isGreaterThanOrEqualTo(5));
    }

    /** Une catégorie est un domaine, pas un format ni un public (issues #43, #47). */
    @Test
    void noFormatOrAudienceWordIsAnActiveKeyword() {
        List<String> forbidden = List.of(
                "atelier", "ateliers", "visite", "visites", "visite guidée", "balade", "promenade", "conférence",
                "conférences", "festival", "rencontre", "animation", "journée", "stage", "escape", "bourse aux", "artisanat",
                "jeune public", "tout public", "famille", "adulte", "enfants");

        List<String> active = jdbcTemplate.queryForList(
                "SELECT keyword FROM category_keyword WHERE active", String.class);

        assertThat(active).doesNotContainAnyElementsOf(forbidden);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "Théâtre : Le Misanthrope;CULTURE",
        "Spectacle de marionnettes pour les petits;CULTURE",
        "Concert de jazz au kiosque;CULTURE",
        "Projection du film « Les Choristes »;CULTURE",
        "Journées européennes du patrimoine;CULTURE,TOURISM",
        "Découverte du patrimoine avec l'office de tourisme;CULTURE,TOURISM",
        "Atelier cuisine et dégustation de fromages;GASTRONOMY",
        "Marché de producteurs du terroir;MARKETS,GASTRONOMY",
        "Grande braderie des commerçants;COMMERCE",
        "Bourse aux jouets;MARKETS",
        "Bourse aux vêtements de la crèche;MARKETS",
        "Exposition d'artisanat local;CULTURE",
        "Trail des collines;SPORT",
        "Tournoi de pétanque;SPORT",
        "Initiation au tir à l'arc;SPORT",
        "Marche nordique au bord du lac;SPORT",
        "Rendez-vous aux jardins;NATURE",
        "Sortie ornithologique avec la LPO : les oiseaux du marais;NATURE",
        "Loto de l'école;LEISURE",
        "Soirée karaoké;LEISURE",
        "Conseil municipal;CITIZEN",
        "Collecte de don du sang;CITIZEN",
        "Exposition sur la biodiversité;CULTURE,NATURE",
        "Marché gourmand avec dégustation et concert;MARKETS,GASTRONOMY,CULTURE",
        "Job dating restauration;EMPLOYMENT",
        "Forum de l'emploi saisonnier;EMPLOYMENT",
        "Atelier CV et entretien d'embauche;EMPLOYMENT",
        "Réunion d'information France Travail;EMPLOYMENT",
        "Découverte des métiers du numérique;EMPLOYMENT",
        "Recrutement par le sport;SPORT,EMPLOYMENT",
        "Bourse aux emplois;EMPLOYMENT"
    })
    void realisticTitlesGetTheExpectedCategories(String title, String codes) {
        assertThat(categoriesOf(title)).isEqualTo(Set.of(codes.split(",")));
    }

    /** Faux positifs à éviter : formats, publics, homographes, accents (marché ≠ marche). */
    @ParameterizedTest
    @ValueSource(strings = {
        "Atelier", "Visite", "Rencontre avec l'équipe", "Jeune public", "Tout public", "Animation",
        "Château gonflable", "Escape", "Conférence", "Bourse aux formations", "Artisanat"
    })
    void formatsAudiencesAndHomographsAreNotClassified(String title) {
        assertThat(categoriesOf(title)).isEmpty();
    }

    @Test
    void marcheNordiqueIsNeverAMarket() {
        assertThat(categoriesOf("Marche nordique")).doesNotContain("MARKETS");
    }

    /** Homographes du domaine emploi : « formation », « apprentissage », « métiers d'art », « stage » seuls. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Formation aux premiers secours", "Apprentissage de la natation", "Journées européennes des métiers d'art",
        "Stage de poterie", "Visite d'entreprise du patrimoine industriel"
    })
    void employmentHomographsAreNotEmployment(String title) {
        assertThat(categoriesOf(title)).doesNotContain("EMPLOYMENT");
    }

    /**
     * Couverture du vocabulaire réel : chaque valeur de l'extraction (catégorie d'origine OpenAgenda,
     * pondérée par son nombre d'activités) est passée comme un titre. Depuis l'issue #49 le domaine
     * emploi/formation a sa catégorie (EMPLOYMENT) : il n'est plus exclu de la mesure.
     */
    @Test
    void realVocabularyCoverageReachesTheFloor() throws IOException {
        long total = 0;
        long covered = 0;
        Map<String, Long> byCategory = new java.util.TreeMap<>();
        for (String[] row : RealExtraction.readExtraction()) {
            String value = row[0];
            if (value.isBlank()) {
                continue;
            }
            long nb = Long.parseLong(row[1]);
            total += nb;
            ClassificationOutcome outcome = classificationService.decide(value, null, null);
            if (outcome.decision().status() == ClassificationStatus.AUTO_CLASSIFIED && outcome.classified()) {
                covered += nb;
                for (String code : outcome.decision().categoryCodes()) {
                    byCategory.merge(code, nb, Long::sum);
                }
            }
        }

        double coverage = (double) covered / total;
        System.out.printf("Couverture du vocabulaire réel : %d/%d = %.1f %% ; par catégorie : %s%n",
                covered, total, 100 * coverage, byCategory.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        assertThat(coverage).isGreaterThanOrEqualTo(MIN_COVERAGE);
        assertThat(byCategory.keySet())
                .contains("CULTURE", "TOURISM", "NATURE", "SPORT", "LEISURE", "MARKETS", "EMPLOYMENT");
    }
}
