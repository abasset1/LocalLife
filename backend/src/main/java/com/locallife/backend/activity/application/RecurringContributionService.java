package com.locallife.backend.activity.application;

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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribution manuelle récurrente (food truck, marché, cours hebdomadaire…) : crée l'activité comme
 * {@link ActivityService#createActivity}, puis le même modèle riche qu'un import récurrent — un
 * {@link Location}, un {@link Schedule} portant la RRULE et ses {@link Occurrence} matérialisées. La
 * recherche (ADR-0006) juge alors l'activité sur ses occurrences : elle n'apparaît que les jours de
 * passage, et plus sur toute la période [startDate, endDate] des colonnes plates.
 */
@Service
public class RecurringContributionService {

    /** Fuseau des contributions manuelles : même repli que la recherche et l'import (ADR-0006). */
    public static final ZoneId CONTRIBUTION_ZONE = ZoneId.of("Europe/Paris");

    /**
     * Occurrences matérialisées au plus sur un an à partir du premier jour : borne aussi les récurrences
     * sans date de fin, dont la RRULE reste conservée sur le {@link Schedule}.
     */
    static final int MATERIALIZATION_HORIZON_YEARS = 1;

    private final ActivityService activityService;
    private final LocationService locationService;
    private final ScheduleService scheduleService;
    private final OccurrenceService occurrenceService;
    private final RecurrenceRuleService recurrenceRuleService;

    public RecurringContributionService(
            ActivityService activityService, LocationService locationService, ScheduleService scheduleService,
            OccurrenceService occurrenceService, RecurrenceRuleService recurrenceRuleService) {
        this.activityService = activityService;
        this.locationService = locationService;
        this.scheduleService = scheduleService;
        this.occurrenceService = occurrenceService;
        this.recurrenceRuleService = recurrenceRuleService;
    }

    /**
     * Crée une activité récurrente ({@code PENDING}, comme toute contribution). Ses colonnes plates
     * {@code startDate}/{@code endDate} couvrent du premier au dernier passage matérialisé.
     *
     * @throws IllegalArgumentException si un champ est invalide ou si aucun jour de passage ne tombe
     *     dans la période demandée
     */
    @Transactional
    public Activity createActivity(
            String title, String description, String category, String address, Long createdBy,
            List<Long> categoryIds, ContributionRecurrence recurrence) {
        Schedule draft = toSchedule(null, null, recurrence);
        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                draft, recurrence.from(), materializationEnd(recurrence));
        if (dates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucun jour de passage ne tombe entre le début et la fin de la récurrence.");
        }

        LocalDateTime firstStart = dates.get(0).atTime(recurrence.startTime());
        LocalDateTime lastEnd = endOf(dates.get(dates.size() - 1), recurrence);
        Activity activity = activityService.createActivity(
                title, description, category, address, firstStart.toString(), lastEnd.toString(), createdBy,
                categoryIds);

        Location location = locationService.findOrCreate(new Location(
                null, null, activity.address(), activity.postalCode(), activity.city(), null, null, null, null,
                activity.latitude(), activity.longitude(), CONTRIBUTION_ZONE.getId(), null, null, null));
        Schedule schedule = scheduleService.create(toSchedule(activity.id(), location.id(), recurrence));
        occurrenceService.createAll(dates.stream()
                .map(date -> new Occurrence(
                        null, schedule.id(), toInstant(date.atTime(recurrence.startTime())),
                        recurrence.endTime() == null ? null : toInstant(endOf(date, recurrence)),
                        null, OccurrenceService.STATUS_SCHEDULED, false))
                .toList());
        return activity;
    }

    private static Schedule toSchedule(Long activityId, Long locationId, ContributionRecurrence recurrence) {
        return new Schedule(
                null, activityId, locationId, recurrence.startTime(), recurrence.endTime(), recurrence.from(),
                recurrence.until(), CONTRIBUTION_ZONE.getId(), recurrence.recurrenceRule());
    }

    private static LocalDate materializationEnd(ContributionRecurrence recurrence) {
        LocalDate horizon = recurrence.from().plusYears(MATERIALIZATION_HORIZON_YEARS).minusDays(1);
        return recurrence.until() == null || recurrence.until().isAfter(horizon) ? horizon : recurrence.until();
    }

    /** Fin d'un passage : le lendemain si l'heure de fin ne suit pas l'heure de début (soirée tardive). */
    private static LocalDateTime endOf(LocalDate date, ContributionRecurrence recurrence) {
        LocalTime endTime = recurrence.endTime();
        if (endTime == null) {
            return date.atTime(recurrence.startTime());
        }
        return endTime.isAfter(recurrence.startTime()) ? date.atTime(endTime) : date.plusDays(1).atTime(endTime);
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(CONTRIBUTION_ZONE).toInstant();
    }
}
