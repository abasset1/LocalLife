package com.locallife.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Location;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Réutilisation d'un lieu d'événement de même texte à la casse, la ponctuation et l'ordre près (ADR-0011,
 * § 7.2.13, option A). Code postal fictif (99904) pour isoler les données du test.
 */
@SpringBootTest
class LocationReuseIntegrationTest {

    private static final String POSTAL = "99904";
    private static final double LAT = 44.05;
    private static final double METER = 0.000009;

    @Autowired
    private LocationService locationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        created.forEach(id -> jdbcTemplate.update("DELETE FROM location WHERE id = ?", id));
    }

    private Location candidate(String address, double metersNorth, CoordinatePrecision precision) {
        return new Location(null, null, address, POSTAL, "Carpentras", null, null, "FR", null,
                LAT + metersNorth * METER, 5.05, "Europe/Paris", null, null, null, precision);
    }

    private Long findOrCreate(String address, double metersNorth, CoordinatePrecision precision) {
        Long id = locationService.findOrCreate(candidate(address, metersNorth, precision)).id();
        if (!created.contains(id)) {
            created.add(id);
        }
        return id;
    }

    @Test
    void sameTextUpToCaseAndOrder_ShouldReuseTheOldestEventLocation_WithoutChangingIt() {
        Long original = findOrCreate("8 Place Aristide Briand Château de Simiane", 0, CoordinatePrecision.UNKNOWN);

        assertThat(findOrCreate("8 place Aristide Briand Château de Simiane", 5, CoordinatePrecision.UNKNOWN))
                .isEqualTo(original);
        assertThat(findOrCreate("Château de Simiane, 8 place Aristide-Briand", 10, CoordinatePrecision.EXACT))
                .isEqualTo(original);
        assertThat(jdbcTemplate.queryForObject("SELECT address FROM location WHERE id = ?", String.class, original))
                .isEqualTo("8 Place Aristide Briand Château de Simiane");
    }

    @Test
    void otherPlace_FarAway_Approximate_OrPermanentPlace_ShouldNotBeReused() {
        Long original = findOrCreate("8 Place Aristide Briand Château de Simiane", 0, CoordinatePrecision.UNKNOWN);

        assertThat(findOrCreate("5 place Aristide Briand Château de Simiane", 2, CoordinatePrecision.UNKNOWN))
                .as("autre numéro").isNotEqualTo(original);
        assertThat(findOrCreate("8 place Aristide Briand Château de Simiane", 60, CoordinatePrecision.UNKNOWN))
                .as("au-delà de 30 m").isNotEqualTo(original);
        assertThat(findOrCreate("8 place Aristide Briand Château de Simiane", 3, CoordinatePrecision.APPROXIMATE))
                .as("position approximative").isNotEqualTo(original);

        Long permanent = jdbcTemplate.queryForObject(
                "INSERT INTO location (address, postal_code, city, latitude, longitude, timezone, place_type, "
                        + "place_status) VALUES ('Musée Comtadin 234 boulevard Albin Durand', ?, 'Carpentras', ?, "
                        + "5.05, 'Europe/Paris', 'Museum', 'PUBLISHED') RETURNING id",
                Long.class, POSTAL, LAT + 500 * METER);
        created.add(permanent);
        assertThat(findOrCreate("234 Boulevard Albin Durand Musée Comtadin", 502, CoordinatePrecision.UNKNOWN))
                .as("un lieu permanent n'est jamais réutilisé comme lieu d'événement").isNotEqualTo(permanent);
    }
}
