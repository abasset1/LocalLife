package com.locallife.backend.foodtruck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository;
import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la validation de {@link FoodTruckService#createFoodTruck}
 * (LL-6009 : nom obligatoire et borné, catégorie obligatoire, coordonnées
 * dans les bornes valides, même style que
 * {@code ActivityServiceTest#createActivity}) et la restriction à
 * présences de {@link FoodTruckService#findActiveOn} (ADR-0013).
 */
@ExtendWith(MockitoExtension.class)
class FoodTruckServiceTest {

    @Mock
    private FoodTruckRepository foodTruckRepository;

    @Mock
    private FoodTruckPresenceRepository presenceRepository;

    @Mock
    private OwnedFoodTruckRepository ownedRepository;

    @InjectMocks
    private FoodTruckService foodTruckService;

    @Test
    void createFoodTruck_ShouldSaveWithPublishedStatus_WhenValid() {
        // Given
        when(ownedRepository.insert(eq(7L), any(), eq(FoodTruckStatus.PUBLISHED))).thenReturn(1L);
        when(foodTruckRepository.findById(1L)).thenReturn(Optional.of(new FoodTruck(
                1L, "Le Camion qui Fume", "Burgers gourmet", null, null, "burger", "https://example.com",
                "PUBLISHED")));

        // When
        FoodTruck result = foodTruckService.createFoodTruck(7L,
                "Le Camion qui Fume", "Burgers gourmet", 43.29, 5.37, "burger", "https://example.com");

        // Then : propriétaire enregistré (PRO-01 lot C) ; la position devient une présence permanente (ADR-0013).
        assertThat(result.name()).isEqualTo("Le Camion qui Fume");
        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(presenceRepository).insertPermanent(1L, 43.29, 5.37);
        assertThat(result.latitude()).isEqualTo(43.29);
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenNameIsBlank() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                "  ", "desc", 43.29, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenNameTooLong() {
        String tooLong = "a".repeat(256);

        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                tooLong, "desc", 43.29, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenCategoryIsBlank() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                "Le Camion qui Fume", "desc", 43.29, 5.37, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenCategoryIsNull() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                "Le Camion qui Fume", "desc", 43.29, 5.37, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenLatitudeOutOfRange() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                "Le Camion qui Fume", "desc", 91, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenLongitudeOutOfRange() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(7L,
                "Le Camion qui Fume", "desc", 43.29, 181, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");

        verify(ownedRepository, never()).insert(any(), any(), any());
    }

    @Test
    void findActiveOn_ShouldDelegateToPresencesOfThatDay() {
        LocalDate day = LocalDate.of(2026, 10, 19);
        when(presenceRepository.findActiveOn(day, null)).thenReturn(List.of());

        assertThat(foodTruckService.findActiveOn(day)).isEmpty();
        verify(presenceRepository).findActiveOn(day, null);
    }

}
