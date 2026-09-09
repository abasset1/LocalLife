package com.locallife.backend.place.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.place.domain.Location;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11001), même patron que
 * {@code ActivityRepositoryIntegrationTest} : chaque test est englobé
 * dans une transaction annulée à la fin. Couvre la persistance de base
 * (dont la compatibilité avec la colonne PostGIS {@code geo_location},
 * alimentée par le trigger {@code location_set_geo_location} — voir
 * {@code V17__create_location_table.sql}) ; aucune requête géographique
 * dédiée n'existe encore sur {@link LocationRepository} (hors périmètre
 * de ce ticket, voir sa javadoc).
 */
@SpringBootTest
@Transactional
class LocationRepositoryIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    private Location namedLocationAt(double latitude, double longitude) {
        String uniqueName = "test-location-" + UUID.randomUUID();
        return new Location(
                null, uniqueName, "10 Rue de la République", "84000", "Avignon", "Vaucluse",
                "Provence-Alpes-Côte d'Azur", "FR", "84007", latitude, longitude, "Europe/Paris",
                null, null, null);
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Location saved = locationRepository.save(namedLocationAt(43.9493, 4.8055));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findById_ShouldReturnSavedLocation_WithAllFieldsIntact() {
        Location location = namedLocationAt(43.9493, 4.8055);
        Location saved = locationRepository.save(location);

        Optional<Location> found = locationRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(location.name());
        assertThat(found.get().address()).isEqualTo(location.address());
        assertThat(found.get().postalCode()).isEqualTo(location.postalCode());
        assertThat(found.get().city()).isEqualTo(location.city());
        assertThat(found.get().department()).isEqualTo(location.department());
        assertThat(found.get().region()).isEqualTo(location.region());
        assertThat(found.get().countryCode()).isEqualTo(location.countryCode());
        assertThat(found.get().insee()).isEqualTo(location.insee());
        assertThat(found.get().latitude()).isEqualTo(location.latitude());
        assertThat(found.get().longitude()).isEqualTo(location.longitude());
        assertThat(found.get().timezone()).isEqualTo(location.timezone());
    }

    @Test
    void save_ShouldSucceed_WhenCoordinatesAreAbsent() {
        // La colonne PostGIS geo_location doit rester NULL sans erreur (trigger, voir javadoc de la classe)
        // plutôt que d'empêcher l'insertion : un lieu peut être connu sans être encore géocodé.
        Location location = new Location(
                null, "test-location-" + UUID.randomUUID(), "10 Rue de la République", null, null, null, null,
                null, null, null, null, null, null, null, null);

        Location saved = locationRepository.save(location);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.latitude()).isNull();
        assertThat(saved.longitude()).isNull();
    }

    @Test
    void findAll_ShouldIncludeSavedLocation() {
        Location saved = locationRepository.save(namedLocationAt(43.9493, 4.8055));

        assertThat(locationRepository.findAll()).extracting(Location::id).contains(saved.id());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenLocationDoesNotExist() {
        assertThat(locationRepository.findById(-1L)).isEmpty();
    }
}
