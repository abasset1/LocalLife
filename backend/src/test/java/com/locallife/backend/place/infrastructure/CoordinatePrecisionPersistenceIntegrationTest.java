package com.locallife.backend.place.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Location;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Précision géographique en base (ADR-0011, phase A, migration V61), sur PostgreSQL réel. Chaque test travaille
 * dans une transaction annulée.
 */
@SpringBootTest
class CoordinatePrecisionPersistenceIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityCoordinatePrecisionRepository precisionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void location_ShouldPersistPrecision_AndDefaultToUnknown() {
        transactionTemplate.executeWithoutResult(status -> {
            Location approximate = locationRepository.save(new Location(null, "Centre ville", null, null, "Apt",
                    null, null, null, null, 43.876, 5.396, null, null, null, null, CoordinatePrecision.APPROXIMATE));
            Location legacy = locationRepository.save(new Location(null, "Théâtre", null, null, "Apt",
                    null, null, null, null, 43.877, 5.397, null, null, null, null));

            assertThat(locationRepository.findById(approximate.id()).orElseThrow().coordinatePrecision())
                    .isEqualTo(CoordinatePrecision.APPROXIMATE);
            assertThat(locationRepository.findById(legacy.id()).orElseThrow().coordinatePrecision())
                    .isEqualTo(CoordinatePrecision.UNKNOWN);
            status.setRollbackOnly();
        });
    }

    @Test
    void columns_ShouldRejectUnknownValues() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO location (name, coordinate_precision) VALUES ('x', 'PRECISE')")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO activity (title, category, latitude, longitude, status, start_date, source_id, "
                        + "coordinate_precision) VALUES ('x', 'x', 43.9, 4.8, 'PUBLISHED', now(), "
                        + "(SELECT min(id) FROM source), 'PRECISE')")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activityPrecision_ShouldSurviveOrdinaryActivitySaves() {
        transactionTemplate.executeWithoutResult(status -> {
            Long sourceId = jdbcTemplate.queryForObject("SELECT min(id) FROM source", Long.class);
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO activity (title, category, latitude, longitude, status, start_date, source_id) "
                            + "VALUES ('Fête au village', 'fete', 43.876, 5.396, 'PUBLISHED', now(), ?) RETURNING id",
                    Long.class, sourceId);
            jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                    + "VALUES (?, (SELECT min(id) FROM category))", id);
            assertThat(precisionRepository.findByActivityId(id)).isEqualTo(CoordinatePrecision.UNKNOWN);

            precisionRepository.update(id, CoordinatePrecision.APPROXIMATE);
            // Copie de l'activité, comme un changement de statut : la colonne n'est pas un champ d'Activity.
            Activity loaded = activityRepository.findById(id).orElseThrow();
            activityRepository.save(new Activity(loaded.id(), loaded.title() + " (modifiée)", loaded.description(),
                    loaded.category(), loaded.latitude(), loaded.longitude(), loaded.startDate(),
                    LocalDateTime.now().plusDays(1), "ARCHIVED", loaded.sourceId(), loaded.importKey(), loaded.url(),
                    loaded.address(), loaded.city(), loaded.postalCode(), loaded.longDescription(),
                    loaded.conditions(), loaded.ageMin(), loaded.ageMax(), loaded.createdBy()));

            assertThat(precisionRepository.findByActivityIds(List.of(id)))
                    .containsEntry(id, CoordinatePrecision.APPROXIMATE);
            status.setRollbackOnly();
        });
    }
}
