package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recherche par zone avec une carte dézoomée à l'échelle du monde (zone de
 * plus de 180° de longitude), contre la base réelle — chaque test annulé en
 * fin de transaction.
 */
@SpringBootTest
@Transactional
class ActivityWithinBoundsWideIntegrationTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long lyonActivityId;

    @BeforeEach
    void setUp() {
        lyonActivityId = jdbcTemplate.queryForObject(
                "INSERT INTO activity (title, latitude, longitude, status, start_date, source_id) "
                        + "VALUES ('Zone large : Lyon', 45.76, 4.83, 'PUBLISHED', ?, "
                        + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                Long.class, LocalDateTime.now().plusDays(2));
        jdbcTemplate.update(
                "INSERT INTO activity_category (activity_id, category_id) SELECT ?, id FROM category WHERE code = 'SPORT'",
                lyonActivityId);
    }

    private List<Long> idsWithin(double swLatitude, double swLongitude, double neLatitude, double neLongitude) {
        return activityRepository.findWithinBounds(
                        swLatitude, swLongitude, neLatitude, neLongitude, "PUBLISHED", null, null, null)
                .stream()
                .map(Activity::id)
                .toList();
    }

    @Test
    void shouldFindActivity_WhenMapIsZoomedOutToTheWorld() {
        // Zone réellement envoyée par la carte web au dézoom maximal (225° de longitude).
        assertThat(idsWithin(-59.7, -107.5, 84.8, 117.4)).contains(lyonActivityId);
        assertThat(idsWithin(-85.0, -180.0, 85.0, 180.0)).contains(lyonActivityId);
    }

    @Test
    void shouldStillFilterOnLatitude_ForWideZones() {
        assertThat(idsWithin(-60.0, -180.0, 10.0, 180.0)).doesNotContain(lyonActivityId);
    }

    @Test
    void shouldKeepUsualBehaviour_ForRegularZones() {
        assertThat(idsWithin(45.0, 4.0, 46.5, 5.5)).contains(lyonActivityId);
        assertThat(idsWithin(30.0, -80.0, 40.0, -60.0)).doesNotContain(lyonActivityId);
    }

}
