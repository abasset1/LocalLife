package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.domain.ActivitySecondaryCategory;
import com.locallife.backend.activityclassification.domain.CategoryKeyword;
import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.ActivitySecondaryCategoryRepository;
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
    private ActivitySecondaryCategoryRepository secondaryCategoryRepository;

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
        assertThat(keywords).extracting(CategoryKeyword::keyword).doesNotContain("marche", "theatre");
    }

    @Test
    void nullSourceKeywords_TitleKeyword_ShouldAutoClassifyAndTraceTheKeyword() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Grand marché de Noël", null, null));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        assertThat(activity.primaryCategoryId()).isEqualTo(categoryId("MARKETS"));
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

        assertThat(singleActivity(sourceId).primaryCategoryId()).isEqualTo(categoryId("MARKETS"));
    }

    @Test
    void sourceKeywordsPresent_ContradictoryTitle_ShouldNotBeUsed() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        // « théâtre » → CULTURE (keyword source) ; le titre « marché » (MARKETS) ne doit pas l'emporter.
        importOne(sourceName, item(sourceName, "Grand marché de Noël", "théâtre", List.of("théâtre")));

        Activity activity = singleActivity(sourceId);
        assertThat(activity.primaryCategoryId()).isEqualTo(categoryId("CULTURE"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.classificationSource()).isNull();
        assertThat(record.matchedKeywords()).isNull();
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
        assertThat(record.classificationSource()).isNull();
    }

    @Test
    void marcheNordique_ShouldNotMatchTheAccentedKeywordMarche() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Marche nordique", null, List.of()));

        assertThat(result.created()).isZero();
        assertThat(activityRepository.findBySourceId(sourceId)).isEmpty();
        ClassificationRecord record = singleRecordWithoutActivity(sourceId, ClassificationStatus.UNCLASSIFIED);
        assertThat(record.ruleId()).isEqualTo("UNCLASSIFIED:NO_SIGNAL");
        assertThat(record.activityId()).isNull();
    }

    @Test
    void keywordsOfTwoCategoriesWithTheSamePriority_ShouldNeedReviewAndNeverCreateAnActivity() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, "Concert au marché de Noël", null, List.of()));

        assertThat(result.created()).isZero();
        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(activityRepository.findBySourceId(sourceId)).isEmpty();
        ClassificationRecord record = singleRecordWithoutActivity(sourceId, ClassificationStatus.NEEDS_REVIEW);
        assertThat(record.reason()).isEqualTo("AMBIGUOUS_PRIMARY");
        assertThat(record.secondaryCategoryCodes()).isNull();
        assertThat(record.classificationSource()).isEqualTo("TITLE_KEYWORD");
        assertThat(split(record.candidateCategoryCodes(), ",")).containsExactlyInAnyOrder("MARKETS", "CULTURE");
        assertThat(split(record.matchedKeywords(), "\\|")).containsExactlyInAnyOrder("marché", "concert");
    }

    @Test
    void severalKeywordsOfTheSameCategory_ShouldAutoClassify() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Marché, brocante et vide grenier", null, List.of()));

        Activity activity = singleActivity(sourceId);
        assertThat(activity.primaryCategoryId()).isEqualTo(categoryId("MARKETS"));
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
                "zumba", "SPORT");

        importOne(sourceName, item(sourceName, "Cours de Zumba en plein air", null, List.of()));

        assertThat(singleActivity(sourceId).primaryCategoryId()).isEqualTo(categoryId("SPORT"));
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
        Long firstCategory = singleActivity(sourceId).primaryCategoryId();

        jdbcTemplate.update("UPDATE category_keyword SET active = false WHERE keyword = ?", "marché");
        ImportResult second = importOne(sourceName, first);

        assertThat(second.updated()).isEqualTo(1);
        assertThat(singleActivity(sourceId).primaryCategoryId()).isEqualTo(firstCategory);
    }

    // --- Issue #43 : catégorie principale + catégories secondaires ---

    private List<Long> secondaryCategoryIds(Long activityId) {
        return secondaryCategoryRepository.findByActivityId(activityId).stream()
                .map(ActivitySecondaryCategory::categoryId)
                .toList();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "Exposition sur la biodiversité;CULTURE;NATURE",
        "Balade à vélo;SPORT;",
        "Marché avec dégustation;MARKETS;GASTRONOMY",
        "Concert avec dégustation;CULTURE;GASTRONOMY",
        "Conférence sur le patrimoine architectural;CULTURE;",
        "Sortie nature avec randonnée;NATURE;SPORT"
    })
    void ticketExamples_ShouldPersistPrimaryAndSecondaryCategories(String title, String primary, String secondary) {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        ImportResult result = importOne(sourceName, item(sourceName, title, null, List.of()));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.needsReview()).isZero();
        Activity activity = singleActivity(sourceId);
        assertThat(activity.primaryCategoryId()).isEqualTo(categoryId(primary));
        List<Long> expectedSecondaries = secondary == null ? List.of() : List.of(categoryId(secondary));
        assertThat(secondaryCategoryIds(activity.id())).containsExactlyElementsOf(expectedSecondaries);
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.status()).isEqualTo(ClassificationStatus.AUTO_CLASSIFIED);
        assertThat(record.ruleId()).isEqualTo("TITLE_KEYWORD:AUTO:" + primary);
        assertThat(record.secondaryCategoryCodes()).isEqualTo(secondary);
    }

    @Test
    void sourceSignalsOfSeveralCategories_ShouldPersistPrimaryAndSecondaries() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        // Tags seuls : « marché » (100) l'emporte sur « dégustation » (50, V41), quel que soit l'ordre.
        importOne(sourceName, item(sourceName, "Titre sans mot-clé", null, List.of("dégustation", "marché")));

        Activity activity = singleActivity(sourceId);
        assertThat(activity.primaryCategoryId()).isEqualTo(categoryId("MARKETS"));
        assertThat(secondaryCategoryIds(activity.id())).containsExactly(categoryId("GASTRONOMY"));
        ClassificationRecord record = recordRepository.findByActivityId(activity.id()).orElseThrow();
        assertThat(record.classificationSource()).isNull();
        assertThat(record.ruleId()).isEqualTo("AUTO:MARKETS");
    }

    @Test
    void singleCategory_ShouldPersistNoSecondaryCategory() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);

        importOne(sourceName, item(sourceName, "Grand marché de Noël", null, List.of()));

        Activity activity = singleActivity(sourceId);
        assertThat(secondaryCategoryIds(activity.id())).isEmpty();
        assertThat(recordRepository.findByActivityId(activity.id()).orElseThrow().secondaryCategoryCodes()).isNull();
    }

    @Test
    void reimport_ShouldKeepTheExistingSecondaryCategories() {
        String sourceName = "Test Source " + UUID.randomUUID();
        Long sourceId = createSource(sourceName);
        CollectedActivity first = item(sourceName, "Marché avec dégustation", null, List.of());
        importOne(sourceName, first);
        Long activityId = singleActivity(sourceId).id();

        jdbcTemplate.update("UPDATE category_keyword SET active = false WHERE keyword = ?", "dégustation");
        ImportResult second = importOne(sourceName, first);

        assertThat(second.updated()).isEqualTo(1);
        assertThat(secondaryCategoryIds(activityId)).containsExactly(categoryId("GASTRONOMY"));
    }

    @Test
    void keywordPriorityReferential_ShouldBeSeededByMigration() {
        Integer degustation = jdbcTemplate.queryForObject(
                "SELECT priority FROM category_keyword WHERE keyword = 'dégustation'", Integer.class);
        Integer concert = jdbcTemplate.queryForObject(
                "SELECT priority FROM category_keyword WHERE keyword = 'concert'", Integer.class);
        Boolean conference = jdbcTemplate.queryForObject(
                "SELECT bool_or(active) FROM category_keyword WHERE keyword IN ('conférence', 'conférences')",
                Boolean.class);
        Integer formatWords = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM category_keyword WHERE active AND keyword IN "
                        + "('balade', 'promenade', 'atelier', 'visite', 'conférence')", Integer.class);

        assertThat(degustation).isEqualTo(50);
        assertThat(concert).isEqualTo(CategoryKeyword.DEFAULT_PRIORITY);
        assertThat(conference).isFalse();
        assertThat(formatWords).isZero();
    }
}
