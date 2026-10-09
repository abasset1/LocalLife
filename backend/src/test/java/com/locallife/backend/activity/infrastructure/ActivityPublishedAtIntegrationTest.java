package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Date de publication (trigger {@code trg_activity_set_published_at}, V54)
 * contre la base réelle, chaque test annulé en fin de transaction.
 */
@SpringBootTest
@Transactional
class ActivityPublishedAtIntegrationTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long insertActivity(String status) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, latitude, longitude, status, source_id) "
                        + "VALUES ('Publication', 43.29, 5.37, ?, (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) "
                        + "RETURNING id",
                Long.class, status);
        jdbcTemplate.update(
                "INSERT INTO activity_category (activity_id, category_id) SELECT ?, id FROM category WHERE code = 'SPORT'",
                id);
        return id;
    }

    private Timestamp publishedAt(Long id) {
        return jdbcTemplate.queryForObject("SELECT published_at FROM activity WHERE id = ?", Timestamp.class, id);
    }

    @Test
    void shouldBeSet_WhenCreatedPublished() {
        assertThat(publishedAt(insertActivity("PUBLISHED"))).isNotNull();
    }

    @Test
    void shouldStayNull_UntilPublication_ThenBeSet() {
        Long id = insertActivity("PENDING");
        assertThat(publishedAt(id)).isNull();

        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED' WHERE id = ?", id);

        assertThat(publishedAt(id)).isNotNull();
    }

    @Test
    void shouldKeepFirstPublicationDate_WhenRepublishedOrOverwritten() {
        Long id = insertActivity("PENDING");
        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED', published_at = '2026-01-01 10:00' WHERE id = ?",
                id);

        jdbcTemplate.update("UPDATE activity SET status = 'ARCHIVED' WHERE id = ?", id);
        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED', published_at = NULL WHERE id = ?", id);
        jdbcTemplate.update("UPDATE activity SET published_at = '2030-01-01 10:00' WHERE id = ?", id);

        assertThat(publishedAt(id).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void repository_ShouldReadPublishedAt_AndNeverEraseItOnSave() {
        Long id = insertActivity("PUBLISHED");
        Activity read = activityRepository.findById(id).orElseThrow();
        assertThat(read.publishedAt()).isNotNull();

        // Mise à jour applicative avec une activité reconstruite sans date de publication.
        activityRepository.save(new Activity(
                read.id(), "Titre modifié", read.description(), read.category(), read.latitude(), read.longitude(),
                read.startDate(), read.endDate(), read.status(), read.sourceId(), read.importKey(), read.url(),
                read.address(), read.city(), read.postalCode(), read.longDescription(), read.conditions(),
                read.ageMin(), read.ageMax(), read.createdBy()));

        Activity reread = activityRepository.findById(id).orElseThrow();
        assertThat(reread.title()).isEqualTo("Titre modifié");
        assertThat(reread.publishedAt()).isEqualTo(read.publishedAt());
    }

}
