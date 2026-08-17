package com.locallife.backend.foodtruck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
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
 * {@code PUBLISHED} de {@link FoodTruckService#findAllPublished}.
 */
@ExtendWith(MockitoExtension.class)
class FoodTruckServiceTest {

    @Mock
    private FoodTruckRepository foodTruckRepository;

    @InjectMocks
    private FoodTruckService foodTruckService;

    @Test
    void createFoodTruck_ShouldSaveWithPublishedStatus_WhenValid() {
        // Given
        when(foodTruckRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FoodTruck result = foodTruckService.createFoodTruck(
                "Le Camion qui Fume", "Burgers gourmet", 43.29, 5.37, "burger", "https://example.com");

        // Then
        assertThat(result.name()).isEqualTo("Le Camion qui Fume");
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.id()).isNull();
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenNameIsBlank() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                "  ", "desc", 43.29, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenNameTooLong() {
        String tooLong = "a".repeat(256);

        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                tooLong, "desc", 43.29, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenCategoryIsBlank() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                "Le Camion qui Fume", "desc", 43.29, 5.37, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenCategoryIsNull() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                "Le Camion qui Fume", "desc", 43.29, 5.37, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenLatitudeOutOfRange() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                "Le Camion qui Fume", "desc", 91, 5.37, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void createFoodTruck_ShouldThrow_WhenLongitudeOutOfRange() {
        assertThatThrownBy(() -> foodTruckService.createFoodTruck(
                "Le Camion qui Fume", "desc", 43.29, 181, "burger", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");

        verify(foodTruckRepository, never()).save(any());
    }

    @Test
    void findAllPublished_ShouldDelegateToRepositoryWithPublishedStatus() {
        // Given
        FoodTruck published = new FoodTruck(
                1L, "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null, "PUBLISHED");
        when(foodTruckRepository.findByStatus("PUBLISHED")).thenReturn(List.of(published));

        // When
        List<FoodTruck> result = foodTruckService.findAllPublished();

        // Then
        assertThat(result).containsExactly(published);
    }

}
