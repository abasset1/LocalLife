package com.locallife.backend.activityclassification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Issue #45 : « une activité a 1..N catégories » est une contrainte réelle en base
 * ({@code V42__activity_category_n_categories.sql}, trigger de contrainte différé vérifié au commit).
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} : un test transactionnel est annulé avant le
 * commit et ne déclencherait jamais la vérification. Les activités créées sont supprimées après chaque
 * test (la suppression d'une activité supprime ses catégories, sans violer la contrainte).
 */
@SpringBootTest
class ActivityCategoryConstraintIntegrationTest {

    @Autowired
    private ActivityClassificationService classificationService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityCategoryRepository activityCategoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdTitles = new ArrayList<>();

    @AfterEach
    void deleteCreatedActivities() {
        for (String title : createdTitles) {
            jdbcTemplate.update("DELETE FROM activity WHERE title = ?", title);
        }
    }

    private Activity newActivity() {
        Long manualSourceId = sourceRepository.findByType("MANUAL").map(Source::id).orElseThrow();
        String title = "category-constraint-" + UUID.randomUUID();
        createdTitles.add(title);
        return new Activity(
                null, title, "description", "sport", 43.9493, 4.8055, LocalDateTime.now(), null, "PUBLISHED",
                manualSourceId, null, null, null, null, null, null, null, null, null, null);
    }

    private Long categoryId(String code) {
        return categoryRepository.findByCode(code).map(Category::id).orElseThrow();
    }

    private List<Long> categoryIdsOf(Long activityId) {
        return classificationService.getCategoryIds(activityId);
    }

    @Test
    void createActivity_ShouldCommitTheActivityWithAllItsCategories() {
        Activity saved = classificationService.createActivity(
                newActivity(), List.of(categoryId("MARKETS"), categoryId("GASTRONOMY"), categoryId("CULTURE")));

        assertThat(activityRepository.findById(saved.id())).isPresent();
        assertThat(categoryIdsOf(saved.id()))
                .containsExactly(categoryId("MARKETS"), categoryId("GASTRONOMY"), categoryId("CULTURE"));
    }

    @Test
    void activityWithoutCategory_ShouldBeRejectedByTheDatabaseAtCommit() {
        Activity activity = newActivity();

        assertThatThrownBy(() -> activityRepository.save(activity))
                .hasStackTraceContaining("must have at least one category");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activity WHERE title = ?", Integer.class, activity.title())).isZero();
    }

    @Test
    void removingTheLastCategory_ShouldBeRejectedByTheDatabase() {
        Activity saved = classificationService.createActivity(newActivity(), List.of(categoryId("SPORT")));

        assertThatThrownBy(() -> activityCategoryRepository.deleteByActivityId(saved.id()))
                .hasStackTraceContaining("must have at least one category");
        assertThat(categoryIdsOf(saved.id())).containsExactly(categoryId("SPORT"));
    }

    @Test
    void replaceCategories_ShouldSwapTheWholeSetInOneTransaction() {
        Activity saved = classificationService.createActivity(newActivity(), List.of(categoryId("SPORT")));

        classificationService.replaceCategories(saved.id(), List.of(categoryId("NATURE"), categoryId("SPORT")));

        assertThat(categoryIdsOf(saved.id())).containsExactly(categoryId("NATURE"), categoryId("SPORT"));
    }

    @Test
    void sameCategoryTwice_ShouldBeStoredOnce() {
        Activity saved = classificationService.createActivity(
                newActivity(), List.of(categoryId("SPORT"), categoryId("SPORT")));

        assertThat(categoryIdsOf(saved.id())).containsExactly(categoryId("SPORT"));
    }
}
