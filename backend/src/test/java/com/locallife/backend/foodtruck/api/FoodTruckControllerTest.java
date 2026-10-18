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
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.SpotKind;
import java.time.LocalDate;
import java.time.LocalTime;
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
    void getAllFoodTrucks_ShouldReturnOneItemPerPresence_AtTheSpotPosition() {
        LocalDate day = LocalDate.of(2026, 10, 19);
        FoodTruckPresence market = new FoodTruckPresence(10L, 1L, "Le Camion qui Fume", "desc", "burger", null,
                43.29, 5.37, "Marché", SpotKind.MARKET, "SA", LocalTime.of(8, 0), LocalTime.of(13, 0), null, null, 7L);
        when(foodTruckService.findActiveOn(day)).thenReturn(List.of(market));

        ResponseEntity<Object> response = foodTruckController.getAllFoodTrucks("2026-10-19", httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).singleElement().satisfies(item -> {
            FoodTruckController.FoodTruckMapResponse mapItem = (FoodTruckController.FoodTruckMapResponse) item;
            assertThat(mapItem.id()).isEqualTo(1L);
            assertThat(mapItem.presenceId()).isEqualTo(10L);
            assertThat(mapItem.latitude()).isEqualTo(43.29);
            assertThat(mapItem.placeId()).isEqualTo(7L);
        });
    }

    @Test
    void getAllFoodTrucks_ShouldRejectAnInvalidDate() {
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/foodtrucks");

        assertThat(foodTruckController.getAllFoodTrucks("19/10/2026", httpRequest).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
