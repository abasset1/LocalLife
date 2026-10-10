package com.locallife.backend.activity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    private static final Long CREATOR_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityService activityService;

    @Mock
    private RecurringContributionService recurringContributionService;

    @Mock
    private ActivityClassificationService activityClassificationService;

    @Mock
    private ActivityMediaService activityMediaService;

    @Mock
    private ActivityLinkService activityLinkService;

    @Mock
    private AccessibilityService accessibilityService;

    @Mock
    private ActivityTagService activityTagService;

    @Mock
    private ActivityMetadataService activityMetadataService;

    @InjectMocks
    private ContributionService contributionService;

    @Test
    void update_ShouldSendActivityBackToValidation_WhenEditedByItsCreator() {
        Activity published = activity("PUBLISHED", CREATOR_ID);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(published));
        Activity updated = activity("PENDING", CREATOR_ID);
        when(activityService.updateContribution(published, "Nouveau titre", "Desc", null, "Lyon", "2026-11-01",
                null, "PENDING", List.of(3L)))
                .thenReturn(updated);

        Optional<Activity> result = contributionService.update(
                1L, CREATOR_ID, false, "Nouveau titre", "Desc", null, "Lyon", "2026-11-01", null, List.of(3L),
                null);

        assertEquals(Optional.of(updated), result);
        // Une activité qui n'est plus récurrente perd son ancien schedule.
        verify(recurringContributionService).clearSchedules(1L);
        verify(recurringContributionService, never()).attachSchedule(any(), any(), any());
    }

    @Test
    void update_ShouldKeepStatus_WhenEditedByAnAdmin() {
        Activity published = activity("PUBLISHED", CREATOR_ID);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(published));
        when(activityService.updateContribution(
                published, "Titre", "Desc", null, "Lyon", null, null, "PUBLISHED", List.of(3L)))
                .thenReturn(published);

        Optional<Activity> result = contributionService.update(
                1L, 99L, true, "Titre", "Desc", null, "Lyon", null, null, List.of(3L), null);

        assertEquals("PUBLISHED", result.orElseThrow().status());
    }

    @Test
    void update_ShouldRebuildSchedule_WhenRecurrenceProvided() {
        Activity pending = activity("PENDING", CREATOR_ID);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(pending));
        ContributionRecurrence recurrence = ContributionRecurrence.of(
                List.of("MO"), "11:30", "14:00", "2026-11-02", "2026-11-09", LocalDate.of(2026, 10, 10));
        List<LocalDate> dates = List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));
        when(recurringContributionService.planDates(recurrence)).thenReturn(dates);
        Activity updated = activity("PENDING", CREATOR_ID);
        when(activityService.updateContribution(pending, "Food truck", "Desc", null, "Lyon", "2026-11-02T11:30",
                "2026-11-09T14:00", "PENDING", List.of(3L)))
                .thenReturn(updated);

        contributionService.update(
                1L, CREATOR_ID, false, "Food truck", "Desc", null, "Lyon", "ignorée", "ignorée", List.of(3L),
                recurrence);

        verify(recurringContributionService).clearSchedules(1L);
        verify(recurringContributionService).attachSchedule(updated, recurrence, dates);
    }

    @Test
    void update_ShouldBeDenied_WhenRequesterIsNeitherCreatorNorAdmin() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("PUBLISHED", CREATOR_ID)));

        assertThrows(ActivityAccessDeniedException.class, () -> contributionService.update(
                1L, OTHER_USER_ID, false, "Titre", "Desc", null, "Lyon", null, null, List.of(3L), null));
        verify(activityService, never()).updateContribution(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_ShouldBeRejected_WhenActivityIsImported() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("PUBLISHED", null)));

        assertThrows(IllegalArgumentException.class, () -> contributionService.update(
                1L, CREATOR_ID, true, "Titre", "Desc", null, "Lyon", null, null, List.of(3L), null));
    }

    @Test
    void update_ShouldBeRejected_WhenActivityIsCancelled() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("CANCELLED", CREATOR_ID)));

        assertThrows(IllegalArgumentException.class, () -> contributionService.update(
                1L, CREATOR_ID, false, "Titre", "Desc", null, "Lyon", null, null, List.of(3L), null));
    }

    @Test
    void update_ShouldReturnEmpty_WhenActivityDoesNotExist() {
        when(activityRepository.findById(1L)).thenReturn(Optional.empty());

        assertTrue(contributionService.update(
                1L, CREATOR_ID, false, "Titre", "Desc", null, "Lyon", null, null, List.of(3L), null).isEmpty());
    }

    @Test
    void delete_ShouldRemoveActivityAndEverythingAttached_WhenRequestedByCreator() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("PUBLISHED", CREATOR_ID)));

        assertTrue(contributionService.delete(1L, CREATOR_ID, false));

        verify(recurringContributionService).clearSchedules(1L);
        verify(activityMediaService).deleteByActivityId(1L);
        verify(activityLinkService).deleteByActivityId(1L);
        verify(accessibilityService).deleteByActivityId(1L);
        verify(activityTagService).deleteByActivityId(1L);
        verify(activityMetadataService).deleteByActivityId(1L);
        verify(activityClassificationService).replaceActivityTypes(1L, List.of());
        verify(activityClassificationService).replaceAudiences(1L, List.of());
        verify(activityClassificationService).setOrganizerType(1L, null);
        verify(activityRepository).deleteById(1L);
    }

    @Test
    void delete_ShouldBeDenied_WhenRequesterIsNeitherCreatorNorAdmin() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("PUBLISHED", CREATOR_ID)));

        assertThrows(ActivityAccessDeniedException.class,
                () -> contributionService.delete(1L, OTHER_USER_ID, false));
        verify(activityRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_ShouldBeAllowed_ForAnAdmin() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity("PENDING", CREATOR_ID)));

        assertTrue(contributionService.delete(1L, 99L, true));
        verify(activityRepository).deleteById(eq(1L));
    }

    @Test
    void delete_ShouldReturnFalse_WhenActivityDoesNotExist() {
        when(activityRepository.findById(1L)).thenReturn(Optional.empty());

        assertFalse(contributionService.delete(1L, CREATOR_ID, false));
    }

    private static Activity activity(String status, Long createdBy) {
        return new Activity(1L, "Titre", "Desc", null, 45.76, 4.83, LocalDateTime.of(2026, 11, 1, 10, 0), null,
                status, 1L, null, null, "Lyon", "Lyon", "69001", null, null, null, null, createdBy);
    }
}
