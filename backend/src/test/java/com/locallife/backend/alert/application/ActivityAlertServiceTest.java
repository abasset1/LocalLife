package com.locallife.backend.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.subscription.application.PlanLimitException;
import com.locallife.backend.subscription.application.PlanLimits;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAlertServiceTest {

    @Mock
    private ActivityAlertRepository activityAlertRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private PlanLimits planLimits;

    @InjectMocks
    private ActivityAlertService activityAlertService;

    private void givenCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                new Category(1L, "SPORT", true, LocalDateTime.now(), LocalDateTime.now(), Set.of()),
                new Category(2L, "CULTURE", true, LocalDateTime.now(), LocalDateTime.now(), Set.of())));
    }

    @Test
    void createAlert_ShouldStoreOfficialCity_AndCleanedCategories() {
        givenCategories();
        when(geocodingService.resolveCity("lyon", null, null)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert created = activityAlertService.createAlert(
                1L, Arrays.asList("SPORT", " CULTURE", "SPORT", null), " lyon ", null, null, 10, null, null, null, null);

        assertEquals("Lyon", created.city());
        assertEquals(45.76, created.latitude());
        assertEquals(List.of("CULTURE", "SPORT"), created.categoryCodes());
        // Sans précision (site web) : e-mail seul.
        assertTrue(created.notifyEmail());
        assertFalse(created.notifyPush());
        verify(activityAlertRepository).create(any(), argThat(token -> token.length() == 64));
    }

    @Test
    void createAlert_ShouldUsePosition_WhenProvided() {
        givenCategories();
        when(geocodingService.resolveCity(null, 43.21, 5.54)).thenReturn(new Coordinates(43.2, 5.5, "Cassis", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert created = activityAlertService.createAlert(
                1L, List.of("SPORT"), null, 43.21, 5.54, 20, null, null, null, null);

        assertEquals("Cassis", created.city());
    }

    @Test
    void createAlert_ShouldRequireAtLeastOneCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of(), "Lyon", null, null, 10, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, null, "Lyon", null, null, 10, null, null, null, null));
        verify(activityAlertRepository, never()).create(any(), anyString());
    }

    @Test
    void createAlert_ShouldRejectUnknownCategory_InvalidRadius_AndMissingCity() {
        givenCategories();

        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("NOPE"), "Lyon", null, null, 10, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(
                        1L, List.of("SPORT"), "Lyon", null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Lyon", null, null, 51, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), " ", null, null, 10, null, null, null, null));
        verify(geocodingService, never()).resolveCity(any(), any(), any());
    }

    @Test
    void createAlert_ShouldRejectWhenLimitReached() {
        givenCategories();
        when(activityAlertRepository.countByUserId(1L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new PlanLimitException("Limite atteinte")).when(planLimits)
                .checkCanAddAlert(1L, 1);

        assertThrows(PlanLimitException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Lyon", null, null, 10, null, null, null, null));
        verify(geocodingService, never()).resolveCity(any(), any(), any());
    }

    @Test
    void createAlert_ShouldPropagateCityNotFound() {
        givenCategories();
        when(geocodingService.resolveCity("Zzzz", null, null)).thenThrow(new CityNotFoundException("introuvable"));

        assertThrows(CityNotFoundException.class,
                () -> activityAlertService.createAlert(1L, List.of("SPORT"), "Zzzz", null, null, 10, null, null, null, null));
        verify(activityAlertRepository, never()).create(any(), anyString());
    }

    @Test
    void deleteAlert_ShouldThrow_WhenNotOwned() {
        when(activityAlertRepository.deleteByIdAndUserId(5L, 1L)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> activityAlertService.deleteAlert(1L, 5L));
    }

    @Test
    void unsubscribe_ShouldIgnoreBlankToken() {
        assertFalse(activityAlertService.unsubscribe(" "));
        verify(activityAlertRepository, never()).deleteByUnsubscribeToken(eq(" "));
    }

    @Test
    void createAlert_ShouldAcceptPushOnly_AndRejectNoChannel() {
        givenCategories();
        when(geocodingService.resolveCity("Lyon", null, null)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert pushOnly = activityAlertService.createAlert(
                1L, List.of("SPORT"), "Lyon", null, null, 10, null, false, true, null);

        assertFalse(pushOnly.notifyEmail());
        assertTrue(pushOnly.notifyPush());
        assertThrows(IllegalArgumentException.class,
                () -> activityAlertService.createAlert(
                        1L, List.of("SPORT"), "Lyon", null, null, 10, null, false, false, null));
    }

    @Test
    void updateChannels_ShouldRejectNoChannel_AndUnknownAlert() {
        assertThrows(IllegalArgumentException.class, () -> activityAlertService.updateChannels(1L, 5L, false, false));
        when(activityAlertRepository.updateChannels(5L, 1L, false, true)).thenReturn(false);
        assertThrows(NoSuchElementException.class, () -> activityAlertService.updateChannels(1L, 5L, false, true));
    }

    @Test
    void createAlert_ShouldAllowFoodTrucksWithoutCategory() {
        when(geocodingService.resolveCity("Lyon", null, null)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert foodTrucksOnly = activityAlertService.createAlert(
                1L, List.of(), "Lyon", null, null, 10, true, null, null, null);

        assertTrue(foodTrucksOnly.includeFoodTrucks());
        assertTrue(foodTrucksOnly.categoryCodes().isEmpty());
    }


    @Test
    void normalizeKeywords_ShouldTrimDeduplicateAndKeepOrder() {
        assertEquals(List.of("marché de Noël", "jazz"), ActivityAlertService.normalizeKeywords(
                Arrays.asList("  marché   de Noël ", "jazz", "MARCHE DE NOEL", " ", null, "Jazz")));
        assertEquals(List.of(), ActivityAlertService.normalizeKeywords(null));
        assertThrows(IllegalArgumentException.class, () -> ActivityAlertService.normalizeKeywords(List.of("a")));
        assertThrows(IllegalArgumentException.class,
                () -> ActivityAlertService.normalizeKeywords(List.of("x".repeat(61))));
        assertThrows(IllegalArgumentException.class, () -> ActivityAlertService.normalizeKeywords(List.of("!!!")));
        assertThrows(IllegalArgumentException.class, () -> ActivityAlertService.normalizeKeywords(
                List.of("k1", "k2", "k3", "k4", "k5", "k6", "k7", "k8", "k9", "k10", "k11")));
    }

    @Test
    void createAlert_WithKeywordsOnly_ShouldNotRequireACategory_ButAPaidPlan() {
        when(geocodingService.resolveCity("Lyon", null, null)).thenReturn(new Coordinates(45.76, 4.83, "Lyon", null));
        when(activityAlertRepository.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAlert created = activityAlertService.createAlert(
                1L, List.of(), "Lyon", null, null, 10, null, null, null, List.of("jazz"));

        assertEquals(List.of("jazz"), created.keywords());
        assertEquals(List.of(), created.categoryCodes());
        verify(planLimits).checkCanUseAlertKeywords(1L);

        doThrow(new PlanLimitException("Réservé à Premium")).when(planLimits).checkCanUseAlertKeywords(2L);
        assertThrows(PlanLimitException.class, () -> activityAlertService.createAlert(
                2L, List.of(), "Lyon", null, null, 10, null, null, null, List.of("jazz")));
    }

    @Test
    void updateKeywords_ShouldLetAnyoneRemoveThem_ButKeepSomethingToWatch() {
        ActivityAlert withCategory = new ActivityAlert(3L, 1L, "Lyon", 45.76, 4.83, 10, List.of("SPORT"), false, true,
                false, LocalDateTime.now(), List.of("yoga"));
        ActivityAlert keywordsOnly = new ActivityAlert(4L, 1L, "Lyon", 45.76, 4.83, 10, List.of(), false, true,
                false, LocalDateTime.now(), List.of("jazz"));
        when(activityAlertRepository.findByUserId(1L))
                .thenReturn(List.of(withCategory, keywordsOnly))
                .thenReturn(List.of(withCategory.withKeywords(List.of()), keywordsOnly));

        assertEquals(List.of(), activityAlertService.updateKeywords(1L, 3L, List.of()).keywords());
        verify(activityAlertRepository).replaceKeywords(3L, 1L, List.of());
        verify(planLimits, never()).checkCanUseAlertKeywords(anyLong());

        assertThrows(IllegalArgumentException.class, () -> activityAlertService.updateKeywords(1L, 4L, List.of()));
        assertThrows(NoSuchElementException.class, () -> activityAlertService.updateKeywords(1L, 99L, List.of()));
    }

    @Test
    void updateKeywords_ShouldRequireAPaidPlanToAddThem() {
        ActivityAlert alert = new ActivityAlert(3L, 1L, "Lyon", 45.76, 4.83, 10, List.of("SPORT"), false, true,
                false, LocalDateTime.now());
        when(activityAlertRepository.findByUserId(1L)).thenReturn(List.of(alert));
        doThrow(new PlanLimitException("Réservé à Premium")).when(planLimits).checkCanUseAlertKeywords(1L);

        assertThrows(PlanLimitException.class,
                () -> activityAlertService.updateKeywords(1L, 3L, List.of("yoga")));
        verify(activityAlertRepository, never()).replaceKeywords(anyLong(), anyLong(), any());
    }
}
