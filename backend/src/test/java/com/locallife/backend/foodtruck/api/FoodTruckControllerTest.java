package com.locallife.backend.foodtruck.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.pro.application.ProAccessPolicy;
import com.locallife.backend.user.domain.Role;
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

    @Mock
    private ProAccessPolicy proAccessPolicy;

    private final JwtAuthentication pro = new JwtAuthentication(7L, "pro@example.com", Role.PRO);

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
    void createFoodTruck_ShouldBeForbidden_WithoutAProAccount() {
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/foodtrucks");
        when(proAccessPolicy.hasProAccess(7L)).thenReturn(false);

        assertThat(foodTruckController.createFoodTruck(new FoodTruckController.CreateFoodTruckRequest(
                "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null), pro, httpRequest).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createFoodTruck_ShouldReturnCreated_WhenValid() {
        // Given
        when(proAccessPolicy.hasProAccess(7L)).thenReturn(true);
        FoodTruck created = new FoodTruck(
                1L, "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null, "PUBLISHED");
        when(foodTruckService.createFoodTruck(eq(7L), anyString(), any(), anyDouble(), anyDouble(), anyString(), any()))
                .thenReturn(created);

        FoodTruckController.CreateFoodTruckRequest request = new FoodTruckController.CreateFoodTruckRequest(
                "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null);

        // When
        ResponseEntity<Object> response = foodTruckController.createFoodTruck(request, pro, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void createFoodTruck_ShouldReturnBadRequest_WhenServiceThrowsIllegalArgument() {
        // Given
        when(proAccessPolicy.hasProAccess(7L)).thenReturn(true);
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/foodtrucks");
        when(foodTruckService.createFoodTruck(eq(7L), anyString(), any(), anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new IllegalArgumentException("Le champ 'name' est obligatoire."));

        FoodTruckController.CreateFoodTruckRequest request = new FoodTruckController.CreateFoodTruckRequest(
                "", "desc", 43.29, 5.37, "burger", null);

        // When
        ResponseEntity<Object> response = foodTruckController.createFoodTruck(request, pro, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

}
