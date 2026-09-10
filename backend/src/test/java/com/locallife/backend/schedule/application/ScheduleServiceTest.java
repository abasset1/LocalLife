package com.locallife.backend.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la délégation de {@link ScheduleService} vers
 * {@link ScheduleRepository} (LL-11002), et la validation de
 * {@code recurrenceRule} avant persistance (LL-11003, critère
 * d'acceptation « règle persistée »). {@link RecurrenceRuleService}
 * n'est volontairement pas mocké : c'est une logique pure sans
 * dépendance, plus simple et plus fidèle à tester avec une vraie
 * instance qu'avec un mock — voir {@link RecurrenceRuleServiceTest}
 * pour la couverture détaillée de son comportement.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, new RecurrenceRuleService());
    }

    private Schedule schedule(Long id, Long activityId) {
        return new Schedule(
                id, activityId, 10L, LocalTime.of(11, 30), LocalTime.of(14, 0),
                LocalDate.of(2026, 9, 9), null, "Europe/Paris", null);
    }

    private Schedule scheduleWithRecurrence(Long id, Long activityId, String recurrenceRule) {
        return new Schedule(
                id, activityId, 10L, LocalTime.of(11, 30), LocalTime.of(14, 0),
                LocalDate.of(2026, 9, 8), null, "Europe/Paris", recurrenceRule);
    }

    @Test
    void create_ShouldDelegateToRepository() {
        Schedule toCreate = schedule(null, 1L);
        Schedule saved = schedule(1L, 1L);
        when(scheduleRepository.save(toCreate)).thenReturn(saved);

        Schedule result = scheduleService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_ShouldSave_WhenRecurrenceRuleIsValid() {
        Schedule toCreate = scheduleWithRecurrence(null, 1L, "FREQ=WEEKLY;BYDAY=TU");
        Schedule saved = scheduleWithRecurrence(1L, 1L, "FREQ=WEEKLY;BYDAY=TU");
        when(scheduleRepository.save(toCreate)).thenReturn(saved);

        Schedule result = scheduleService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_ShouldThrow_AndNeverSave_WhenRecurrenceRuleIsInvalid() {
        Schedule invalid = scheduleWithRecurrence(null, 1L, "n'importe quoi");

        assertThatThrownBy(() -> scheduleService.create(invalid)).isInstanceOf(IllegalArgumentException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void findById_ShouldDelegateToRepository() {
        Schedule found = schedule(1L, 1L);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(found));

        Optional<Schedule> result = scheduleService.findById(1L);

        assertThat(result).contains(found);
    }

    @Test
    void findByActivityId_ShouldReturnAllSchedulesForActivity() {
        // Couvre directement le critère d'acceptation « une activité peut avoir plusieurs schedules ».
        Schedule first = schedule(1L, 42L);
        Schedule second = schedule(2L, 42L);
        when(scheduleRepository.findByActivityId(42L)).thenReturn(List.of(first, second));

        List<Schedule> result = scheduleService.findByActivityId(42L);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void findAll_ShouldDelegateToRepository() {
        Schedule found = schedule(1L, 1L);
        when(scheduleRepository.findAll()).thenReturn(List.of(found));

        List<Schedule> result = scheduleService.findAll();

        assertThat(result).containsExactly(found);
    }
}
