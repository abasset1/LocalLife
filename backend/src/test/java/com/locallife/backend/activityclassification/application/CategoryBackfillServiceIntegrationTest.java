package com.locallife.backend.activityclassification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activityclassification.application.CategoryBackfillService.BackfillResult;
import com.locallife.backend.category.application.CategoryAdminService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Une catégorie créée après l'import (« Marchés de Noël ») est ajoutée aux activités à venir déjà importées dont le
 * titre ou les tags d'origine correspondent à ses mots-clés, sans retirer leurs catégories existantes.
 */
@SpringBootTest
@Transactional
class CategoryBackfillServiceIntegrationTest {

    @Autowired
    private CategoryAdminService categoryAdminService;

    @Autowired
    private CategoryBackfillService backfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long insertActivity(String title, String categoryCode, LocalDateTime startDate) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, latitude, longitude, status, start_date, source_id) "
                        + "VALUES (?, 45.76, 4.83, 'PUBLISHED', ?, (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) "
                        + "RETURNING id",
                Long.class, title, startDate);
        jdbcTemplate.update(
                "INSERT INTO activity_category (activity_id, category_id) SELECT ?, id FROM category WHERE code = ?",
                id, categoryCode);
        return id;
    }

    private List<String> categoryCodes(Long activityId) {
        return jdbcTemplate.queryForList("""
                SELECT c.code FROM activity_category ac JOIN category c ON c.id = ac.category_id
                WHERE ac.activity_id = ? ORDER BY ac.id
                """, String.class, activityId);
    }

    @Test
    void addsTheNewCategoryToMatchingUpcomingActivities_WithoutRemovingTheirCategories() {
        // Le rattrapage porte sur toute la base : de vraies activités à venir (un import réel laissé par un autre
        // test ou par la base locale, comme les marchés de Noël de novembre) fausseraient les compteurs. Archivées
        // ici, dans la transaction du test, annulée à la fin.
        jdbcTemplate.update("UPDATE activity SET status = 'ARCHIVED' WHERE status <> 'ARCHIVED'");
        LocalDateTime nextMonth = LocalDateTime.now().plusMonths(1);
        Long byTitle = insertActivity("Grand Marché de Noël de Lyon", "MARKETS", nextMonth);
        Long unrelated = insertActivity("Concert du soir", "CULTURE", nextMonth);
        Long past = insertActivity("Marché de Noël", "MARKETS", LocalDateTime.now().minusYears(1));
        Long byTag = insertActivity("Village des lutins", "CULTURE", nextMonth);
        jdbcTemplate.update("""
                INSERT INTO classification_record (source_id, import_key, activity_id, status, rule_id,
                    source_tags, title, created_at, updated_at)
                VALUES ((SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1), 'test-lutins', ?, 'AUTO_CLASSIFIED',
                    'AUTO:CULTURE', 'Marché de Noël,fête', 'Village des lutins', now(), now())
                """, byTag);

        categoryAdminService.createCategory("CHRISTMAS_MARKETS_TEST", "Marchés de Noël", null);
        categoryAdminService.addKeyword("CHRISTMAS_MARKETS_TEST", "marché de noël", "ALL");

        BackfillResult result = backfillService.applyToExistingActivities("CHRISTMAS_MARKETS_TEST").orElseThrow();

        assertThat(result.added()).isEqualTo(2);
        assertThat(categoryCodes(byTitle)).containsExactly("MARKETS", "CHRISTMAS_MARKETS_TEST");
        assertThat(categoryCodes(byTag)).containsExactly("CULTURE", "CHRISTMAS_MARKETS_TEST");
        assertThat(categoryCodes(unrelated)).containsExactly("CULTURE");
        assertThat(categoryCodes(past)).containsExactly("MARKETS");

        // Rejouable : rien de plus à ajouter.
        assertThat(backfillService.applyToExistingActivities("CHRISTMAS_MARKETS_TEST").orElseThrow().added())
                .isZero();
    }
}
