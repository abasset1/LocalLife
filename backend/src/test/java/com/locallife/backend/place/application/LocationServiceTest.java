package com.locallife.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre le garde-fou de {@link LocationService#create} (LL-11001,
 * critère « aucun lieu ne doit être créé artificiellement à partir de
 * données insuffisantes ») et la délégation simple des autres méthodes.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    private LocationService locationService;

    private Location emptyLocation() {
        return new Location(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        locationService = new LocationService(locationRepository);
    }

    @Test
    void create_ShouldThrow_WhenNameAddressAndCoordinatesAllAbsent() {
        Location location = emptyLocation();

        assertThatThrownBy(() -> locationService.create(location)).isInstanceOf(IllegalArgumentException.class);

        verify(locationRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrow_WhenNameIsBlank() {
        // Un nom uniquement composé d'espaces n'est pas une donnée identifiante réelle.
        Location location = new Location(
                null, "   ", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> locationService.create(location)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldSave_WhenNameIsPresent() {
        Location location = new Location(
                null, "Place Pie", null, null, null, null, null, null, null, null, null, null, null, null, null);
        Location saved = new Location(
                1L, "Place Pie", null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(locationRepository.save(location)).thenReturn(saved);

        Location result = locationService.create(location);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_ShouldSave_WhenOnlyAddressIsPresent() {
        Location location = new Location(
                null, null, "10 Rue de la République", null, null, null, null, null, null, null, null, null, null,
                null, null);
        when(locationRepository.save(location)).thenReturn(location);

        Location result = locationService.create(location);

        assertThat(result).isEqualTo(location);
    }

    @Test
    void create_ShouldSave_WhenOnlyCoordinatesArePresent() {
        Location location = new Location(
                null, null, null, null, null, null, null, null, null, 43.9493, 4.8055, null, null, null, null);
        when(locationRepository.save(location)).thenReturn(location);

        Location result = locationService.create(location);

        assertThat(result).isEqualTo(location);
    }

    @Test
    void create_ShouldThrow_WhenOnlyLatitudeIsPresent() {
        // Coordonnées incomplètes : ni identifiantes par elles-mêmes, ni exploitables par PostGIS
        // (le trigger location_set_geo_location exige les deux, voir V17__create_location_table.sql).
        Location location = new Location(
                null, null, null, null, null, null, null, null, null, 43.9493, null, null, null, null, null);

        assertThatThrownBy(() -> locationService.create(location)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findById_ShouldDelegateToRepository() {
        Location location = new Location(
                1L, "Place Pie", null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        Optional<Location> result = locationService.findById(1L);

        assertThat(result).contains(location);
    }

    @Test
    void findAll_ShouldDelegateToRepository() {
        Location location = new Location(
                1L, "Place Pie", null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(locationRepository.findAll()).thenReturn(List.of(location));

        List<Location> result = locationService.findAll();

        assertThat(result).containsExactly(location);
    }
}
