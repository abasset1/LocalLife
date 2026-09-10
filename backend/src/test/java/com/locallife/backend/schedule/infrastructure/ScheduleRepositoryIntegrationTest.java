package com.locallife.backend.schedule.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11002), même patron que
 * {@code ActivityRepositoryIntegrationTest}/{@code LocationRepositoryIntegrationTest} :
 * chaque test est englobé dans une transaction annulée à la fin. Couvre la
 * persistance de base, le lien vers une activité pouvant avoir plusieurs
 * schedules (critère d'acceptation explicite), un schedule sans lieu
 * résolu (locationId nullable), et la contrainte FOREIGN KEY sur
 * activity_id (V18__create_schedule_table.sql).
 */
@SpringBootTest
@Transactional
class ScheduleRepositoryIntegrationTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private Long manualSourceId() {
        return sourceRepository.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException("Source MANUAL introuvable — migration V8 manquante ?"));
    }

    private Activity testActivity() {
        String uniqueTitle = "test-activity-" + UUID.randomUUID();
        return activityRepository.save(new Activity(
                null, uniqueTitle, "description", "sport",
                43.9493, 4.8055, LocalDateTime.now(), null, "PUBLISHED", manualSourceId(), null, null,
                null, null, null, null, null, null, null));
    }

    private Location testLocation() {
        return locationRepository.save(new Location(
                null, "test-location-" + UUID.randomUUID(), "10 Rue de la République", "84000", "Avignon",
                null, null, "FR", null, 43.9493, 4.8055, "Europe/Paris", null, null, null));
    }

    private Schedule scheduleFor(Long activityId, Long locationId) {
        return new Schedule(
                null, activityId, locationId, LocalTime.of(11, 30), LocalTime.of(14, 0),
                LocalDate.of(2026, 9, 9), null, "Europe/Paris", null);
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Activity activity = testActivity();
        Location location = testLocation();

        Schedule saved = scheduleRepository.save(scheduleFor(activity.id(), location.id()));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void save_ShouldSucceed_WhenLocationIsAbsent() {
        // Un lieu peut ne pas encore être résolu (locationId nullable, voir la javadoc de Schedule).
        Activity activity = testActivity();

        Schedule saved = scheduleRepository.save(scheduleFor(activity.id(), null));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.locationId()).isNull();
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        Schedule orphanSchedule = scheduleFor(-1L, null);

        assertThatThrownBy(() -> scheduleRepository.save(orphanSchedule))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_ShouldReturnSavedSchedule_WithAllFieldsIntact() {
        Activity activity = testActivity();
        Location location = testLocation();
        Schedule schedule = scheduleFor(activity.id(), location.id());

        Schedule saved = scheduleRepository.save(schedule);
        Optional<Schedule> found = scheduleRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().activityId()).isEqualTo(activity.id());
        assertThat(found.get().locationId()).isEqualTo(location.id());
        assertThat(found.get().startTime()).isEqualTo(schedule.startTime());
        assertThat(found.get().endTime()).isEqualTo(schedule.endTime());
        assertThat(found.get().validFrom()).isEqualTo(schedule.validFrom());
        assertThat(found.get().validUntil()).isNull();
        assertThat(found.get().timezone()).isEqualTo(schedule.timezone());
    }

    @Test
    void findByActivityId_ShouldReturnAllSchedulesForActivity_AndExcludeOthers() {
        // Critère d'acceptation explicite : « une activité peut avoir plusieurs schedules ».
        Activity activity = testActivity();
        Activity otherActivity = testActivity();
        Schedule first = scheduleRepository.save(scheduleFor(activity.id(), null));
        Schedule second = scheduleRepository.save(scheduleFor(activity.id(), null));
        Schedule other = scheduleRepository.save(scheduleFor(otherActivity.id(), null));

        List<Schedule> result = scheduleRepository.findByActivityId(activity.id());

        assertThat(result).extracting(Schedule::id).contains(first.id(), second.id());
        assertThat(result).extracting(Schedule::id).doesNotContain(other.id());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenScheduleDoesNotExist() {
        assertThat(scheduleRepository.findById(-1L)).isEmpty();
    }
}
