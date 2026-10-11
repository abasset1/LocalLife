package com.locallife.backend.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.application.ActivityBulkDeletionService.Criteria;
import com.locallife.backend.activity.application.ActivityBulkDeletionService.DeletionCountChangedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression d'activités par un administrateur, contre la base réelle. Transactionnel : tout est
 * annulé à la fin de chaque test, y compris la suppression de toutes les activités.
 */
@SpringBootTest
@Transactional
class ActivityBulkDeletionServiceIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2031, 5, 10);

    @Autowired
    private ActivityBulkDeletionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sourceId;

    @BeforeEach
    void createTestSource() {
        sourceId = jdbcTemplate.queryForObject(
                "INSERT INTO source (name, type, status) VALUES (?, 'API', 'ACTIVE') RETURNING id",
                Long.class, "Suppression test " + UUID.randomUUID());
    }

    private Long insertActivity(Long source, String status, LocalDateTime startDate) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, latitude, longitude, status, start_date, source_id) "
                        + "VALUES ('Suppression test', 45.76, 4.83, ?, ?, ?) RETURNING id",
                Long.class, status, startDate, source);
        jdbcTemplate.update(
                "INSERT INTO activity_category (activity_id, category_id) SELECT ?, id FROM category WHERE code = 'SPORT'",
                id);
        return id;
    }

    private boolean exists(Long activityId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM activity WHERE id = ?)", Boolean.class, activityId));
    }

    private Criteria bySource(String status, LocalDate from, LocalDate to) {
        return new Criteria(sourceId, status, from, to, false);
    }

    @Test
    void dependentTables_ShouldCoverEveryTableReferencingActivityWithoutCascade() {
        // Une nouvelle table liée à activity sans ON DELETE CASCADE bloquerait la suppression :
        // elle doit être ajoutée à DEPENDENT_TABLES (schedule est vidée à part, avec ses occurrences).
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT c.conrelid::regclass::text FROM pg_constraint c "
                        + "WHERE c.contype = 'f' AND c.confrelid = 'activity'::regclass AND c.confdeltype <> 'c'",
                String.class);

        assertThat(tables).isNotEmpty();
        assertThat(tables.stream().filter(table -> !table.equals("schedule")))
                .allMatch(ActivityBulkDeletionService.DEPENDENT_TABLES::contains);
    }

    @Test
    void count_ShouldApplyEachCriterion() {
        insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));
        insertActivity(sourceId, "PUBLISHED", DAY.plusDays(1).atTime(23, 30));
        insertActivity(sourceId, "PENDING", DAY.plusDays(5).atTime(9, 0));

        assertThat(service.count(bySource(null, null, null))).isEqualTo(3);
        assertThat(service.count(bySource("PUBLISHED", null, null))).isEqualTo(2);
        // Bornes incluses, sur toute la journée de fin.
        assertThat(service.count(bySource(null, DAY, DAY.plusDays(1)))).isEqualTo(2);
        assertThat(service.count(bySource(null, DAY.plusDays(2), null))).isEqualTo(1);
    }

    @Test
    void delete_ShouldRemoveMatchingActivitiesAndWhatIsAttached_AndKeepTheOthers() {
        Long target = insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));
        Long kept = insertActivity(sourceId, "PENDING", DAY.atTime(10, 0));
        Long scheduleId = jdbcTemplate.queryForObject(
                "INSERT INTO schedule (activity_id, start_time) VALUES (?, '10:00') RETURNING id", Long.class, target);
        jdbcTemplate.update("INSERT INTO occurrence (schedule_id, start_at, status) VALUES (?, ?, 'SCHEDULED')",
                scheduleId, DAY.atTime(10, 0));
        jdbcTemplate.update("INSERT INTO activity_link (activity_id, type, value) VALUES (?, 'WEBSITE', 'https://x.fr')",
                target);
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, role, created_at, email_verified) "
                        + "VALUES ('bulk-del', 'bulk-delete@example.com', 'USER', now(), true) RETURNING id",
                Long.class);
        jdbcTemplate.update("INSERT INTO activity_likes (user_id, activity_id) VALUES (?, ?)", userId, target);

        int deleted = service.delete(bySource("PUBLISHED", null, null), 1);

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(target)).isFalse();
        assertThat(exists(kept)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule WHERE activity_id = ?", Long.class,
                target)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM occurrence WHERE schedule_id = ?", Long.class,
                scheduleId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_link WHERE activity_id = ?",
                Long.class, target)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_likes WHERE activity_id = ?",
                Long.class, target)).isZero();
    }

    @Test
    void delete_ShouldDeleteNothing_WhenCountChangedSincePreview() {
        Long first = insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));
        insertActivity(sourceId, "PUBLISHED", DAY.atTime(11, 0));

        assertThatThrownBy(() -> service.delete(bySource(null, null, null), 1))
                .isInstanceOf(DeletionCountChangedException.class)
                .hasMessageContaining("2 au lieu de 1");
        assertThat(exists(first)).isTrue();
    }

    @Test
    void delete_ShouldRequireACriterionOrExplicitAll() {
        Long id = insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));
        Criteria none = new Criteria(null, null, null, null, false);

        assertThatThrownBy(() -> service.count(none)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delete(none, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(exists(id)).isTrue();
    }

    @Test
    void delete_ShouldRejectInvalidCriteria() {
        assertThatThrownBy(() -> service.count(bySource("INCONNU", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.count(bySource(null, DAY.plusDays(1), DAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteAll_ShouldRemoveEveryActivity() {
        insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));
        Criteria all = new Criteria(null, null, null, null, true);
        long total = service.count(all);

        assertThat(service.delete(all, total)).isEqualTo((int) total);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Long.class)).isZero();
    }

    @Test
    void deleteOne_ShouldDeleteImportedActivity_AndReportMissingOne() {
        Long id = insertActivity(sourceId, "PUBLISHED", DAY.atTime(10, 0));

        assertThat(service.deleteOne(id)).isTrue();
        assertThat(exists(id)).isFalse();
        assertThat(service.deleteOne(id)).isFalse();
    }
}
