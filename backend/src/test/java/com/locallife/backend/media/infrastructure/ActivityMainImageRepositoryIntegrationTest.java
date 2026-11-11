package com.locallife.backend.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Image principale résolue en lot pour les listes de la carte, sur PostgreSQL réel. Transaction annulée.
 */
@SpringBootTest
class ActivityMainImageRepositoryIntegrationTest {

    @Autowired
    private ActivityMainImageRepository mainImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void findMainImageUrls_ShouldReturnFirstMediaByPosition_AndOmitActivitiesWithoutMedia() {
        transactionTemplate.executeWithoutResult(status -> {
            Long withMedia = insertActivity("Marché");
            Long withoutMedia = insertActivity("Concert");
            Long second = insertMedia("https://example.com/second.jpg");
            Long first = insertMedia("https://example.com/first.jpg");
            jdbcTemplate.update("INSERT INTO activity_media (activity_id, media_id, position) VALUES (?, ?, 1)",
                    withMedia, second);
            jdbcTemplate.update("INSERT INTO activity_media (activity_id, media_id, position) VALUES (?, ?, 0)",
                    withMedia, first);

            Map<Long, String> urls = mainImageRepository.findMainImageUrls(List.of(withMedia, withoutMedia));

            assertThat(urls).containsExactly(Map.entry(withMedia, "https://example.com/first.jpg"));
            assertThat(mainImageRepository.findMainImageUrls(List.of())).isEmpty();
            status.setRollbackOnly();
        });
    }

    private Long insertActivity(String title) {
        Long sourceId = jdbcTemplate.queryForObject("SELECT min(id) FROM source", Long.class);
        return jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, category, latitude, longitude, status, start_date, source_id) "
                        + "VALUES (?, 'fete', 43.876, 5.396, 'PUBLISHED', now(), ?) RETURNING id",
                Long.class, title, sourceId);
    }

    private Long insertMedia(String url) {
        return jdbcTemplate.queryForObject("INSERT INTO media (url) VALUES (?) RETURNING id", Long.class, url);
    }
}
