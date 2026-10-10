package com.locallife.backend.activity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.RecurrenceRuleService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringContributionServiceTest {

    @Mock
    private ActivityService activityService;

    @Mock
    private LocationService locationService;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private OccurrenceService occurrenceService;

    private RecurringContributionService service;

    /** Jeudi 1er octobre 2026. */
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        service = new RecurringContributionService(
                activityService, locationService, scheduleService, occurrenceService, new RecurrenceRuleService());
    }

    @Test
    void createActivity_ShouldCreateScheduleAndWeeklyOccurrences() {
        ContributionRecurrence recurrence = ContributionRecurrence.of(
                List.of("TU", "TH"), "11:30", "14:00", "2026-10-01", "2026-10-14", THURSDAY);
        Activity created = activity();
        when(activityService.createActivity(
                "Food truck", "Burgers", null, "Place du marché, Lyon", "2026-10-01T11:30", "2026-10-13T14:00",
                7L, List.of(1L)))
                .thenReturn(created);
        when(locationService.findOrCreate(any()))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));
        when(scheduleService.create(any())).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            return new Schedule(40L, schedule.activityId(), schedule.locationId(), schedule.startTime(),
                    schedule.endTime(), schedule.validFrom(), schedule.validUntil(), schedule.timezone(),
                    schedule.recurrenceRule());
        });

        Activity result = service.createActivity(
                "Food truck", "Burgers", null, "Place du marché, Lyon", 7L, List.of(1L), recurrence);

        assertEquals(created, result);
        ArgumentCaptor<Schedule> schedule = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleService).create(schedule.capture());
        assertEquals(5L, schedule.getValue().activityId());
        assertEquals(30L, schedule.getValue().locationId());
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", schedule.getValue().recurrenceRule());
        assertEquals("Europe/Paris", schedule.getValue().timezone());
        assertEquals(LocalDate.of(2026, 10, 14), schedule.getValue().validUntil());

        ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
        verify(locationService).findOrCreate(location.capture());
        assertEquals("Lyon", location.getValue().city());
        assertEquals(45.76, location.getValue().latitude());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrences = ArgumentCaptor.forClass(List.class);
        verify(occurrenceService).createAll(occurrences.capture());
        // Jeudi 1er, mardi 6, jeudi 8, mardi 13 octobre (heure d'été : UTC+2).
        assertEquals(
                List.of(Instant.parse("2026-10-01T09:30:00Z"), Instant.parse("2026-10-06T09:30:00Z"),
                        Instant.parse("2026-10-08T09:30:00Z"), Instant.parse("2026-10-13T09:30:00Z")),
                occurrences.getValue().stream().map(Occurrence::startAt).toList());
        Occurrence first = occurrences.getValue().get(0);
        assertEquals(40L, first.scheduleId());
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), first.endAt());
        assertEquals(OccurrenceService.STATUS_SCHEDULED, first.status());
    }

    @Test
    void createActivity_ShouldEndNextDay_WhenEndTimeIsNotAfterStartTime() {
        ContributionRecurrence recurrence = ContributionRecurrence.of(
                List.of("FR"), "19:00", "01:00", "2026-10-02", "2026-10-02", THURSDAY);
        when(activityService.createActivity(
                anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(), anyList()))
                .thenReturn(activity());
        when(locationService.findOrCreate(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));
        when(scheduleService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createActivity("Marché de nuit", "Nocturne", null, "Lyon", 7L, List.of(1L), recurrence);

        verify(activityService).createActivity("Marché de nuit", "Nocturne", null, "Lyon", "2026-10-02T19:00",
                "2026-10-03T01:00", 7L, List.of(1L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrences = ArgumentCaptor.forClass(List.class);
        verify(occurrenceService).createAll(occurrences.capture());
        assertEquals(Instant.parse("2026-10-02T23:00:00Z"), occurrences.getValue().get(0).endAt());
    }

    @Test
    void createActivity_ShouldMaterializeOneYear_WhenNoEndDate() {
        ContributionRecurrence recurrence =
                ContributionRecurrence.of(List.of("SA"), "08:00", null, "2026-10-03", null, THURSDAY);
        when(activityService.createActivity(
                anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(), anyList()))
                .thenReturn(activity());
        when(locationService.findOrCreate(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));
        when(scheduleService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createActivity("Marché", "Producteurs", null, "Lyon", 7L, List.of(1L), recurrence);

        ArgumentCaptor<Schedule> schedule = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleService).create(schedule.capture());
        assertNull(schedule.getValue().validUntil());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrences = ArgumentCaptor.forClass(List.class);
        verify(occurrenceService).createAll(occurrences.capture());
        // Du samedi 3 octobre 2026 au samedi 2 octobre 2027 inclus.
        assertEquals(53, occurrences.getValue().size());
        assertNull(occurrences.getValue().get(0).endAt());
    }

    @Test
    void createActivity_ShouldRejectRecurrenceWithoutAnyOccurrence() {
        // Du lundi 5 au mercredi 7 octobre : aucun samedi.
        ContributionRecurrence recurrence =
                ContributionRecurrence.of(List.of("SA"), "08:00", null, "2026-10-05", "2026-10-07", THURSDAY);

        assertThrows(IllegalArgumentException.class, () -> service.createActivity(
                "Marché", "Producteurs", null, "Lyon", 7L, List.of(1L), recurrence));
        verify(activityService, never()).createActivity(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Activity activity() {
        return new Activity(5L, "Food truck", "Burgers", null, 45.76, 4.83,
                LocalDateTime.of(2026, 10, 1, 11, 30), LocalDateTime.of(2026, 10, 13, 14, 0), "PENDING", 1L,
                null, null, "Place du marché", "Lyon", "69001", null, null, null, null, 7L);
    }

    private static Location withId(Location location, Long id) {
        return new Location(id, location.name(), location.address(), location.postalCode(), location.city(),
                location.department(), location.region(), location.countryCode(), location.insee(),
                location.latitude(), location.longitude(), location.timezone(), location.website(),
                location.email(), location.phone());
    }
}
