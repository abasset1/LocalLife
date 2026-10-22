package com.locallife.backend.foodtruck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la restriction aux présences de {@link FoodTruckService#findActiveOn} (ADR-0013). La création directe
 * ({@code createFoodTruck}) a été retirée le 22/10/2026 : voir {@code ProFoodTruckService}.
 */
@ExtendWith(MockitoExtension.class)
class FoodTruckServiceTest {

    @Mock
    private FoodTruckRepository foodTruckRepository;

    @Mock
    private FoodTruckPresenceRepository presenceRepository;

    @InjectMocks
    private FoodTruckService foodTruckService;

    @Test
    void findActiveOn_ShouldDelegateToPresencesOfThatDay() {
        LocalDate day = LocalDate.of(2026, 10, 19);
        when(presenceRepository.findActiveOn(day, null)).thenReturn(List.of());

        assertThat(foodTruckService.findActiveOn(day)).isEmpty();
        verify(presenceRepository).findActiveOn(day, null);
    }

}
