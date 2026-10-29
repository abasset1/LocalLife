package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prochaine occurrence affichée par les listes, sur PostgreSQL réel. Chaque test travaille dans une transaction
 * annulée ; la date de référence est fixée pour ne pas dépendre du jour d'exécution.
 */
@SpringBootTest
@Transactional
class ActivityNextOccurrenceRepositoryIntegrationTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 30);

    @Autowired
    private ActivityNextOccurrenceRepository repository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void shouldSkipPastOccurrence_AndReturnNextOneInLocalTime() {
        // Vide-grenier d'Eygalières : deux journées séparées, le 19/04 et le 01/11 (minuit à Paris = 23 h UTC).
        Activity activity = activityWithDates(LocalDateTime.of(2026, 4, 19, 0, 0));
        Long scheduleId = scheduleFor(activity.id());
        occurrence(scheduleId, "2026-04-18T22:00:00Z", "2026-04-19T21:59:00Z", "SCHEDULED");
        occurrence(scheduleId, "2026-10-31T23:00:00Z", "2026-11-01T22:59:00Z", "SCHEDULED");

        Map<Long, LocalDateTime> result = repository.findNextStartByActivityIds(List.of(activity.id()), REFERENCE);

        assertThat(result).containsEntry(activity.id(), LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    void shouldIgnoreCancelledOccurrence() {
        Activity activity = activityWithDates(LocalDateTime.of(2026, 11, 1, 10, 0));
        Long scheduleId = scheduleFor(activity.id());
        occurrence(scheduleId, "2026-11-01T09:00:00Z", "2026-11-01T11:00:00Z", "CANCELLED");
        occurrence(scheduleId, "2026-11-08T09:00:00Z", "2026-11-08T11:00:00Z", "SCHEDULED");

        Map<Long, LocalDateTime> result = repository.findNextStartByActivityIds(List.of(activity.id()), REFERENCE);

        assertThat(result).containsEntry(activity.id(), LocalDateTime.of(2026, 11, 8, 10, 0));
    }

    @Test
    void shouldKeepOngoingOccurrence() {
        // Exposition commencée avant la date de référence et pas encore terminée : c'est elle qui est affichée.
        Activity activity = activityWithDates(LocalDateTime.of(2026, 9, 1, 10, 0));
        Long scheduleId = scheduleFor(activity.id());
        occurrence(scheduleId, "2026-09-01T08:00:00Z", "2026-12-31T17:00:00Z", "SCHEDULED");

        Map<Long, LocalDateTime> result = repository.findNextStartByActivityIds(List.of(activity.id()), REFERENCE);

        assertThat(result).containsEntry(activity.id(), LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    void shouldOmitActivityWithoutUpcomingOccurrence() {
        Activity finished = activityWithDates(LocalDateTime.of(2026, 4, 19, 0, 0));
        occurrence(scheduleFor(finished.id()), "2026-04-18T22:00:00Z", "2026-04-19T21:59:00Z", "SCHEDULED");
        Activity unscheduled = activityWithDates(LocalDateTime.of(2026, 12, 5, 9, 0));

        Map<Long, LocalDateTime> result = repository.findNextStartByActivityIds(
                List.of(finished.id(), unscheduled.id()), REFERENCE);

        assertThat(result).isEmpty();
    }

    private Activity activityWithDates(LocalDateTime startDate) {
        Long manualSourceId = sourceRepository.findByType("MANUAL").map(Source::id).orElseThrow();
        return activityRepository.save(new Activity(
                null, "test-activity-" + UUID.randomUUID(), "description", "vide-greniers",
                43.76, 4.95, startDate, null, "PUBLISHED", manualSourceId, null, null,
                null, null, null, null, null, null, null, null));
    }

    /** Schedule sans lieu : le fuseau par défaut (Europe/Paris) s'applique. */
    private Long scheduleFor(Long activityId) {
        return scheduleRepository.save(new Schedule(
                null, activityId, null, LocalTime.MIDNIGHT, LocalTime.of(23, 59),
                LocalDate.of(2026, 1, 1), null, "Europe/Paris", null)).id();
    }

    private void occurrence(Long scheduleId, String startAt, String endAt, String status) {
        occurrenceRepository.save(new Occurrence(
                null, scheduleId, Instant.parse(startAt), Instant.parse(endAt), null, status, false));
    }
}
