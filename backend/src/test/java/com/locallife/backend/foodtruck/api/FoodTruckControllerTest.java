package com.locallife.backend.foodtruck.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.locallife.backend.foodtruck.application.FoodTruckService;
import com.locallife.backend.foodtruck.domain.FoodTruck;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FoodTruckControllerTest {

    @Mock
    private FoodTruckService foodTruckService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private FoodTruckController foodTruckController;

    @Test
    void getAllFoodTrucks_ShouldReturnPublishedList() {
        // Given
        List<FoodTruck> foodTrucks = List.of(
                new FoodTruck(1L, "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null, "PUBLISHED"));
        when(foodTruckService.findAllPublished()).thenReturn(foodTrucks);

        // When
        ResponseEntity<List<FoodTruck>> response = foodTruckController.getAllFoodTrucks();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void createFoodTruck_ShouldReturnCreated_WhenValid() {
        // Given
        FoodTruck created = new FoodTruck(
                1L, "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null, "PUBLISHED");
        when(foodTruckService.createFoodTruck(anyString(), any(), anyDouble(), anyDouble(), anyString(), any()))
                .thenReturn(created);

        FoodTruckController.CreateFoodTruckRequest request = new FoodTruckController.CreateFoodTruckRequest(
                "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null);

        // When
        ResponseEntity<Object> response = foodTruckController.createFoodTruck(request, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void createFoodTruck_ShouldReturnBadRequest_WhenServiceThrowsIllegalArgument() {
        // Given
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/foodtrucks");
        when(foodTruckService.createFoodTruck(anyString(), any(), anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new IllegalArgumentException("Le champ 'name' est obligatoire."));

        FoodTruckController.CreateFoodTruckRequest request = new FoodTruckController.CreateFoodTruckRequest(
                "", "desc", 43.29, 5.37, "burger", null);

        // When
        ResponseEntity<Object> response = foodTruckController.createFoodTruck(request, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

}
