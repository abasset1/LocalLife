package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.domain.ActivityCategory;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.ActivityCategoryRepository;
import com.locallife.backend.activityclassification.infrastructure.CategoryKeywordRepository;
import com.locallife.backend.activityclassification.infrastructure.ClassificationRecordRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.SingleMockCollectorConfig;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fallback de classification par le titre, de bout en bout : {@code CollectedActivity} →
 * {@code ImportService} → {@code ClassificationService} → catégorie de l'activité et
 * {@code ClassificationRecord} (base réelle, mots-clés seedés par {@code V39}). Seul le
 * {@code Collector} est mocké, comme dans {@code ImportServiceIntegrationTest}.
 */
@SpringBootTest
@Import(SingleMockCollectorConfig.class)
@Transactional
class TitleKeywordClassificationIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryKeywordRepository keywordRepository;

    @Autowired
    private ClassificationRecordRepository recordRepository;

    @Autowired
    private ActivityCategoryRepository activityCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Collector collector;

    @BeforeEach
    void resetCollectorMock() {
        Mockito.reset(collector);
    }

    private Long createSource(String name) {
        return sourceRepository.save(
                new Source(null, name, "API", null, "ACTIVE", null, "agenda-uid-" + UUID.randomUUID(), null)).id();
    }

    private CollectedActivity item(String sourceName, String title, String category, List<String> tags) {
        return new CollectedActivity(
                title, "description", category, "https://example.com", "ext-" + UUID.randomUUID(), sourceName,
                null, null, null, null,
                new CollectedLocation(null, null, null, null, null, null, 43.2965, 5.3698, null),
                List.of(new CollectedTiming(LocalDateTime.now().plusDays(1), null)),
                null, List.of(), List.of(), null, tags, null);
    }

    private ImportResult importOne(String sourceName, CollectedActivity item) {
        when(collector.collect()).thenReturn(List.of(item));
        return importService.importAll().stream()
                .filter(result -> sourceName.equals(result.sourceName()))
                .findFirst()
                .orElseThrow();
    }

    private Long categoryId(String code) {
        return categoryRepository.findByCode(code).map(Category::id).orElseThrow();
    }

    private Activity singleActivity(Long sourceId) {
        List<Activity> activities = activityRepository.findBySourceId(sourceId);
        assertThat(activities).hasSize(1);
        return activities.get(0);
    }

    private ClassificationRecord singleRecordWithoutActivity(Long sourceId, ClassificationStatus status) {
        List<ClassificationRecord> records = recordRepository.findByStatus(status).stream()
                .filter(record -> sourceId.equals(record.sourceId()))
                .toList();
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private static Set<String> split(String value, String regex) {
        return new HashSet<>(Arrays.asList(value.split(regex)));
    }

    @Test
    void keywordReferentialIsStoredInDatabaseOnExistingV1Categories() {
        List<CategoryKeyword> keywords = keywordRepository.findByActiveTrueOrderByIdAsc();

        assertThat(keywords).isNotEmpty();
        assertThat(keywords).allSatisfy(keyword ->
                assertThat(categoryRepository.findById(keyword.categoryId())).isPresent());
        // Accents conservés dans le référentiel.
        assertThat(keywords).extracting(CategoryKeyword::keyword).contains("marché", "théâtre");
        // « marche » (sans accent) n'est pas un mot-clé : « Marche nordique » ne doit jamais devenir un marché.
        // (« theatre » sans accent, lui, est une graphie réellement observée, ajoutée par V43.)
        assertThat(keywords).extracting(CategoryKeyword::keyword).doesNotContain("marche");
    }

    @Test
    void nullSourceKeywords_TitleKeyword_ShouldAutoClassifyAndTraceTheKeyword() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Grand marché de Noël", null, null));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactly(categoryId("MARKETS"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.status()).isEqualTo(ClassificationStatus.AUTO_CLASSIFIED);
        assertThat(record.classificationSource()).isEqualTo("TITLE_KEYWORD");
        assertThat(record.matchedKeywords()).isEqualTo("marché");
        assertThat(record.ruleId()).isEqualTo("TITLE_KEYWORD:AUTO:MARKETS");
        assertThat(record.title()).isEqualTo("Grand marché de Noël");
    }

    @Test
    void emptyOrBlankSourceKeywords_TitleKeyword_ShouldAutoClassify() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        // Une catégorie d'origine blanche est rejetée en amont par NormalizationService (donnée invalide) :
        // le cas « blanc » pertinent ici est celui des tags (null, vide, espaces).
        importOne(sourceName, item(sourceName, "Grand marché de Noël", null, Arrays.asList(null, " ", "")));

        assertThat(categoryIds(singleActivity(sourceId).id())).containsExactly(categoryId("MARKETS"));
    }

    @Test
    void sourceKeywordsPresent_ContradictoryTitle_ShouldNotBeUsed() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        // « théâtre » → CULTURE (keyword source) ; le titre « marché » (MARKETS) ne doit pas l'emporter.
        importOne(sourceName, item(sourceName, "Grand marché de Noël", "théâtre", List.of("théâtre")));

        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactly(categoryId("CULTURE"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        // Décision issue des keywords source (référentiel unique, issue #50) : tracée comme telle, pas le titre.
        assertThat(record.classificationSource()).isEqualTo("SOURCE_KEYWORD");
        assertThat(record.matchedKeywords()).isEqualTo("théâtre");
    }

    @Test
    void unrecognisedSourceKeywords_ShouldNotFallBackToTheTitle() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName,
                item(sourceName, "Grand marché de Noël", "zzz-inconnu", List.of()));

        assertThat(result.created()).isZero();
        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(activityRepository.findBySourceId(sourceId)).isEmpty();
        ClassificationRecord record = singleRecordWithoutActivity(sourceId, ClassificationStatus.NEEDS_REVIEW);
        assertThat(record.classificationSource()).isEqualTo("SOURCE_KEYWORD");
        assertThat(record.reason()).isEqualTo("UNRECOGNIZED");
        assertThat(record.matchedKeywords()).isNull();
    }

    @Test
    void marcheNordique_ShouldNotMatchTheAccentedKeywordMarche() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Marche nordique", null, List.of()));

        // « marche nordique » est un mot-clé SPORT (V43) ; « marché » (MARKETS) ne matche jamais « marche ».
        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactly(categoryId("SPORT"));
        assertThat(recordRepository.findByActivityId(activity.id()).orElseThrow().matchedKeywords())
                .isEqualTo("marche nordique");
    }

    @Test
    void accentedKeywordMarche_ShouldNotMatchAnUnaccentedWord() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Marche des fiertés", null, List.of()));

        assertThat(result.created()).isZero();
        assertThat(activityRepository.findBySourceId(sourceId)).isEmpty();
        ClassificationRecord record = singleRecordWithoutActivity(sourceId, ClassificationStatus.UNCLASSIFIED);
        assertThat(record.ruleId()).isEqualTo("UNCLASSIFIED:NO_SIGNAL");
        assertThat(record.activityId()).isNull();
    }

    @Test
    void keywordsOfTwoCategories_ShouldCreateTheActivityWithBothCategories() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Concert au marché de Noël", null, List.of()));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactlyInAnyOrder(categoryId("MARKETS"), categoryId("CULTURE"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.status()).isEqualTo(ClassificationStatus.AUTO_CLASSIFIED);
        assertThat(record.reason()).isNull();
        assertThat(record.classificationSource()).isEqualTo("TITLE_KEYWORD");
        assertThat(split(record.categoryCodes(), ",")).containsExactlyInAnyOrder("MARKETS", "CULTURE");
        assertThat(split(record.matchedKeywords(), "\\|")).containsExactlyInAnyOrder("marché", "concert");
    }

    @Test
    void severalKeywordsOfTheSameCategory_ShouldAutoClassify() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Marché, brocante et vide grenier", null, List.of()));

        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactly(categoryId("MARKETS"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(split(record.matchedKeywords(), "\\|"))
                .containsExactlyInAnyOrder("marché", "vide-grenier", "brocante");
    }

    @Test
    void keywordsAreReadFromTheDatabase_NotFromJavaCode() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);
        jdbcTemplate.update(
                "INSERT INTO category_keyword (category_id, keyword) SELECT id, ? FROM category WHERE code = ?",
                "zorbing", "SPORT");

        importOne(sourceName, item(sourceName, "Initiation au zorbing en plein air", null, List.of()));

        assertThat(categoryIds(singleActivity(sourceId).id())).containsExactly(categoryId("SPORT"));
    }

    @Test
    void inactiveKeyword_ShouldNotMatch() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);
        jdbcTemplate.update("UPDATE category_keyword SET active = false WHERE keyword = ?", "marché");

        ImportResult result = importOne(sourceName, item(sourceName, "Grand marché de Noël", null, List.of()));

        assertThat(result.created()).isZero();
        assertThat(activityRepository.findBySourceId(sourceId)).isEmpty();
        assertThat(singleRecordWithoutActivity(sourceId, ClassificationStatus.UNCLASSIFIED).ruleId())
                .isEqualTo("UNCLASSIFIED:NO_SIGNAL");
    }

    @Test
    void reimport_ShouldKeepTheExistingCategory_EvenIfTheTitleKeywordChanged() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);
        CollectedActivity first = item(sourceName, "Grand marché de Noël", null, List.of());
        importOne(sourceName, first);
        List<Long> firstCategories = categoryIds(singleActivity(sourceId).id());

        jdbcTemplate.update("UPDATE category_keyword SET active = false WHERE keyword = ?", "marché");
        ImportResult second = importOne(sourceName, first);

        assertThat(second.updated()).isEqualTo(1);
        assertThat(categoryIds(singleActivity(sourceId).id())).isEqualTo(firstCategories);
    }

    // --- Issue #45 : 1..N catégories par activité ---

    private List<Long> categoryIds(Long activityId) {
        return activityCategoryRepository.findByActivityIdOrderByIdAsc(activityId).stream()
                .map(ActivityCategory::categoryId)
                .toList();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "Marché gourmand avec dégustation et concert;MARKETS,GASTRONOMY,CULTURE",
        "Balade nature à vélo;NATURE,SPORT",
        "Exposition de peinture;CULTURE"
    })
    void ticketExamples_ShouldPersistAllDetectedCategories(String title, String codes) {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, title, null, List.of()));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        List<Long> expected = Arrays.stream(codes.split(",")).map(this::categoryId).toList();
        assertThat(categoryIds(activity.id())).containsExactlyInAnyOrderElementsOf(expected);
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.status()).isEqualTo(ClassificationStatus.AUTO_CLASSIFIED);
        assertThat(split(record.categoryCodes(), ",")).isEqualTo(split(codes, ","));
    }

    @Test
    void sourceSignalsOfSeveralCategories_ShouldAllBePersisted() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Titre sans mot-clé", null, List.of("dégustation", "marché")));

        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id())).containsExactly(categoryId("GASTRONOMY"), categoryId("MARKETS"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.classificationSource()).isEqualTo("SOURCE_KEYWORD");
        assertThat(record.matchedKeywords()).isEqualTo("dégustation|marché");
        assertThat(record.ruleId()).isEqualTo("AUTO:GASTRONOMY+MARKETS");
        assertThat(record.categoryCodes()).isEqualTo("GASTRONOMY,MARKETS");
    }

    @Test
    void reimport_ShouldKeepTheExistingCategories() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);
        CollectedActivity first = item(sourceName, "Marché avec dégustation", null, List.of());
        importOne(sourceName, first);
        Long activityId = singleActivity(sourceId).id();

        jdbcTemplate.update("UPDATE category_keyword SET active = false WHERE keyword = ?", "dégustation");
        ImportResult second = importOne(sourceName, first);

        assertThat(second.updated()).isEqualTo(1);
        assertThat(categoryIds(activityId)).containsExactlyInAnyOrder(categoryId("MARKETS"), categoryId("GASTRONOMY"));
    }

    @Test
    void migration_ShouldHaveRemovedThePrimarySecondaryModel() {
        Integer legacyStructures = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE "
                        + "(table_name = 'activity' AND column_name = 'primary_category_id') "
                        + "OR (table_name = 'category_keyword' AND column_name = 'priority') "
                        + "OR table_name = 'activity_secondary_category'", Integer.class);
        Boolean conference = jdbcTemplate.queryForObject(
                "SELECT bool_or(active) FROM category_keyword WHERE keyword IN ('conférence', 'conférences')",
                Boolean.class);
        Integer formatWords = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM category_keyword WHERE active AND keyword IN "
                        + "('balade', 'promenade')", Integer.class);

        assertThat(legacyStructures).isZero();
        // V47 : « conférence » / « conférences », désactivés par V41, sont réactivés (décision du 08/10/2026).
        assertThat(conference).isTrue();
        assertThat(formatWords).isZero();
    }

    // --- Issue #49 : catégorie emploi & formation ---

    @Test
    void employmentSource_ShouldCreateAnActivityInsteadOfNeedingReview() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName,
                item(sourceName, "Job dating restauration", "#TousMobilisés", List.of("Recrutement", "théâtre")));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        assertThat(categoryIds(activity.id()))
                .containsExactlyInAnyOrder(categoryId("EMPLOYMENT"), categoryId("CULTURE"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.status()).isEqualTo(ClassificationStatus.AUTO_CLASSIFIED);
        assertThat(record.reason()).isNull();
    }

    @Test
    void employmentTitle_ShouldBeClassifiedByTheTitleFallback() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Forum de l'emploi et de l'alternance", null, List.of()));

        assertThat(categoryIds(singleActivity(sourceId).id())).containsExactly(categoryId("EMPLOYMENT"));
    }

    @Test
    void employmentCategory_ShouldBeInTheReferentialWithItsLabel() {
        String label = jdbcTemplate.queryForObject(
                "SELECT t.label FROM category c JOIN category_translation t ON t.category_id = c.id "
                        + "WHERE c.code = 'EMPLOYMENT' AND c.active AND t.locale = 'fr-FR'", String.class);

        assertThat(label).isEqualTo("Emploi & formation");
    }
}
