package com.locallife.backend.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.schedule.domain.Schedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Couvre les critères d'acceptation de LL-11003 : règle persistée
 * (validation), calcul des occurrences (quotidien, hebdomadaire, jours
 * de semaine déterminés, date de début, date de fin, absence de date de
 * fin), fuseau horaire pris en compte, aucun format propriétaire
 * inventé (délégation à ical4j pour le parsing, voir
 * {@code docs/02_Architecture/ADR-0003-recurrence-ical4j.md}). Mardi 8,
 * 15, 22, 29 septembre 2026 : mêmes dates que l'exemple de
 * {@code docs/05_Sprints/SPRINT_11.md}, section 7 (LL-11004).
 */
class RecurrenceRuleServiceTest {

    private final RecurrenceRuleService recurrenceRuleService = new RecurrenceRuleService();

    private Schedule weeklyTuesdaySchedule(LocalDate validFrom, LocalDate validUntil) {
        return new Schedule(
                1L, 1L, 10L, LocalTime.of(11, 30), LocalTime.of(14, 0), validFrom, validUntil,
                "Europe/Paris", "FREQ=WEEKLY;BYDAY=TU");
    }

    @Test
    void validate_ShouldAccept_DailyRule() {
        recurrenceRuleService.validate("FREQ=DAILY");
        // Ne doit pas lever : la règle est un RRULE RFC 5545 valide (cas minimum « tous les jours »).
    }

    @Test
    void validate_ShouldAccept_WeeklyRuleWithByDay() {
        recurrenceRuleService.validate("FREQ=WEEKLY;BYDAY=TU");
        // Cas minimum « toutes les semaines » + « jours déterminés de la semaine ».
    }

    @Test
    void validate_ShouldReject_NonRruleText() {
        // Critère d'acceptation « aucun format propriétaire inventé » : un texte qui n'est pas
        // un RRULE RFC 5545 (ex. un format maison quelconque) doit être rejeté, pas toléré.
        assertThatThrownBy(() -> recurrenceRuleService.validate("tous les mardis"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computeOccurrenceDates_ShouldReturnEveryDay_ForDailyRule() {
        Schedule daily = new Schedule(
                1L, 1L, null, null, null, LocalDate.of(2026, 9, 8), null, "Europe/Paris", "FREQ=DAILY");

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                daily, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10));

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10));
    }

    @Test
    void computeOccurrenceDates_ShouldReturnOnlyMatchingWeekday_ForByDayRule() {
        // « jours déterminés de la semaine » : seuls les mardis doivent apparaître.
        Schedule schedule = weeklyTuesdaySchedule(LocalDate.of(2026, 9, 8), null);

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                schedule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 29));
    }

    @Test
    void computeOccurrenceDates_ShouldRespectValidFrom_AsRecurrenceStartDate() {
        // « date de début » : validFrom (8 septembre) précède le 1er septembre dans la fenêtre
        // demandée, mais aucune occurrence ne doit apparaître avant le 8 (le 1er est pourtant
        // aussi un mardi, voir le commentaire du test précédent).
        Schedule schedule = weeklyTuesdaySchedule(LocalDate.of(2026, 9, 8), null);

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                schedule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).doesNotContain(LocalDate.of(2026, 9, 1));
    }

    @Test
    void computeOccurrenceDates_ShouldRespectValidUntil_AsRecurrenceEndDate() {
        // « date de fin » : aucune occurrence après le 22 (validUntil), même si la fenêtre
        // demandée va jusqu'au 30.
        Schedule schedule = weeklyTuesdaySchedule(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 22));

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                schedule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 22));
        assertThat(dates).doesNotContain(LocalDate.of(2026, 9, 29));
    }

    @Test
    void computeOccurrenceDates_ShouldContinueUntilWindowEnd_WhenValidUntilIsAbsent() {
        // « absence de date de fin lorsque la source le permet » : sans validUntil, les
        // occurrences vont jusqu'à la fin de la fenêtre demandée (29 septembre inclus).
        Schedule schedule = weeklyTuesdaySchedule(LocalDate.of(2026, 9, 8), null);

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                schedule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).contains(LocalDate.of(2026, 9, 29));
    }

    @Test
    void computeOccurrenceDates_ShouldReturnSingleDate_ForPunctualSchedule() {
        // recurrenceRule == null : événement ponctuel, voir la javadoc de Schedule.
        Schedule punctual = new Schedule(
                1L, 1L, 10L, LocalTime.of(20, 0), LocalTime.of(23, 0), LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 12), "Europe/Paris", null);

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                punctual, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 12));
    }

    @Test
    void computeOccurrenceDates_ShouldReturnEmpty_WhenPunctualDateIsOutsideWindow() {
        Schedule punctual = new Schedule(
                1L, 1L, 10L, LocalTime.of(20, 0), LocalTime.of(23, 0), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 12), "Europe/Paris", null);

        List<LocalDate> dates = recurrenceRuleService.computeOccurrenceDates(
                punctual, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(dates).isEmpty();
    }

    @Test
    void computeOccurrenceStarts_ShouldCombineDateTimeAndTimezone() {
        // « fuseau horaire pris en compte » : l'heure de début (11:30) doit être interprétée
        // dans le fuseau du schedule (Europe/Paris), pas en UTC.
        Schedule schedule = weeklyTuesdaySchedule(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 8));

        List<ZonedDateTime> starts = recurrenceRuleService.computeOccurrenceStarts(
                schedule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(starts).containsExactly(
                ZonedDateTime.of(2026, 9, 8, 11, 30, 0, 0, ZoneId.of("Europe/Paris")));
    }
}
