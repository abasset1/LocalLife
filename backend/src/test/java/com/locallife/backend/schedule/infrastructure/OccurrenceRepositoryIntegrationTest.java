package com.locallife.backend.schedule.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.Instant;
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
 * Tests d'intégration contre la base réelle (LL-11004/LL-11005), même
 * patron que {@code ScheduleRepositoryIntegrationTest} : chaque test est
 * englobé dans une transaction annulée à la fin. Couvre la persistance
 * de base (dont {@code start_at}/{@code end_at} en {@code TIMESTAMPTZ},
 * voir la javadoc de {@code Occurrence}), le lien vers un schedule
 * pouvant avoir plusieurs occurrences, une occurrence avec un lieu
 * effectif distinct du lieu du schedule, la contrainte FOREIGN KEY sur
 * {@code schedule_id} (V19), et le drapeau {@code is_exception}
 * (V20/LL-11005).
 */
@SpringBootTest
@Transactional
class OccurrenceRepositoryIntegrationTest {

    @Autowired
    private OccurrenceRepository occurrenceRepository;

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

    private Location testLocation() {
        return locationRepository.save(new Location(
                null, "test-location-" + UUID.randomUUID(), "10 Rue de la République", "84000", "Avignon",
                null, null, "FR", null, 43.9493, 4.8055, "Europe/Paris", null, null, null));
    }

    private Schedule testSchedule(Long locationId) {
        Activity activity = activityRepository.save(new Activity(
                null, "test-activity-" + UUID.randomUUID(), "description", "sport",
                43.9493, 4.8055, LocalDateTime.now(), null, "PUBLISHED", manualSourceId(), null, null,
                null, null, null));
        return scheduleRepository.save(new Schedule(
                null, activity.id(), locationId, LocalTime.of(11, 30), LocalTime.of(14, 0),
                LocalDate.of(2026, 9, 8), null, "Europe/Paris", "FREQ=WEEKLY;BYDAY=TU"));
    }

    private Occurrence occurrenceFor(Long scheduleId, Long locationId, Instant startAt) {
        return new Occurrence(null, scheduleId, startAt, startAt.plusSeconds(9000), locationId, "SCHEDULED", false);
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Location location = testLocation();
        Schedule schedule = testSchedule(location.id());

        Occurrence saved = occurrenceRepository.save(
                occurrenceFor(schedule.id(), location.id(), Instant.parse("2026-09-08T09:30:00Z")));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findById_ShouldReturnSavedOccurrence_WithAllFieldsIntact() {
        Location location = testLocation();
        Schedule schedule = testSchedule(location.id());
        Instant startAt = Instant.parse("2026-09-08T09:30:00Z");
        Occurrence occurrence = occurrenceFor(schedule.id(), location.id(), startAt);

        Occurrence saved = occurrenceRepository.save(occurrence);
        Optional<Occurrence> found = occurrenceRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().scheduleId()).isEqualTo(schedule.id());
        assertThat(found.get().locationId()).isEqualTo(location.id());
        assertThat(found.get().startAt()).isEqualTo(occurrence.startAt());
        assertThat(found.get().endAt()).isEqualTo(occurrence.endAt());
        assertThat(found.get().status()).isEqualTo("SCHEDULED");
    }

    @Test
    void save_ShouldSucceed_WhenEffectiveLocationDiffersFromScheduleLocation() {
        // « lieu effectif » : une occurrence peut avoir un locationId différent de celui de son
        // schedule (voir la javadoc d'Occurrence — LL-11005, "22 septembre -> Marseille").
        Location scheduleLocation = testLocation();
        Location effectiveLocation = testLocation();
        Schedule schedule = testSchedule(scheduleLocation.id());

        Occurrence saved = occurrenceRepository.save(
                occurrenceFor(schedule.id(), effectiveLocation.id(), Instant.parse("2026-09-22T09:30:00Z")));

        assertThat(saved.locationId()).isEqualTo(effectiveLocation.id());
        assertThat(saved.locationId()).isNotEqualTo(schedule.locationId());
    }

    @Test
    void save_ShouldFail_WhenScheduleDoesNotExist() {
        Occurrence orphanOccurrence = occurrenceFor(-1L, null, Instant.parse("2026-09-08T09:30:00Z"));

        assertThatThrownBy(() -> occurrenceRepository.save(orphanOccurrence))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByScheduleId_ShouldReturnAllOccurrencesForSchedule_AndExcludeOthers() {
        // Critère d'acceptation « relation avec Schedule » : un schedule récurrent se
        // matérialise en plusieurs occurrences.
        Schedule schedule = testSchedule(null);
        Schedule otherSchedule = testSchedule(null);
        Occurrence first = occurrenceRepository.save(
                occurrenceFor(schedule.id(), null, Instant.parse("2026-09-08T09:30:00Z")));
        Occurrence second = occurrenceRepository.save(
                occurrenceFor(schedule.id(), null, Instant.parse("2026-09-15T09:30:00Z")));
        Occurrence other = occurrenceRepository.save(
                occurrenceFor(otherSchedule.id(), null, Instant.parse("2026-09-08T09:30:00Z")));

        List<Occurrence> result = occurrenceRepository.findByScheduleId(schedule.id());

        assertThat(result).extracting(Occurrence::id).contains(first.id(), second.id());
        assertThat(result).extracting(Occurrence::id).doesNotContain(other.id());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenOccurrenceDoesNotExist() {
        assertThat(occurrenceRepository.findById(-1L)).isEmpty();
    }

    @Test
    void save_ShouldPersistExceptionalFlag_WhenOccurrenceIsCancelled() {
        // LL-11005, « une exception est identifiable » + « 15 septembre → annulé ».
        Schedule schedule = testSchedule(null);
        Occurrence cancelled = new Occurrence(
                null, schedule.id(), Instant.parse("2026-09-15T09:30:00Z"),
                Instant.parse("2026-09-15T12:00:00Z"), null, "CANCELLED", true);

        Occurrence saved = occurrenceRepository.save(cancelled);
        Optional<Occurrence> found = occurrenceRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo("CANCELLED");
        assertThat(found.get().exceptional()).isTrue();
    }

    @Test
    void save_ShouldDefaultToNonExceptional_ForNormallyMaterializedOccurrence() {
        // LL-11005, « 29 septembre → normal » : une occurrence sans particularité n'est pas une
        // exception.
        Schedule schedule = testSchedule(null);

        Occurrence saved = occurrenceRepository.save(
                occurrenceFor(schedule.id(), null, Instant.parse("2026-09-29T09:30:00Z")));

        assertThat(saved.exceptional()).isFalse();
    }

    @Test
    void save_ExceptionalOccurrence_ShouldNotModifySchedule() {
        // Critère d'acceptation LL-11005 « elle ne détruit pas la règle générale » : modifier une
        // occurrence (ici, la déplacer et changer son lieu) ne touche jamais le schedule associé.
        Location originalLocation = testLocation();
        Location relocatedLocation = testLocation();
        Schedule schedule = testSchedule(originalLocation.id());

        occurrenceRepository.save(new Occurrence(
                null, schedule.id(), Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T12:30:00Z"), relocatedLocation.id(), "SCHEDULED", true));

        Optional<Schedule> unchangedSchedule = scheduleRepository.findById(schedule.id());
        assertThat(unchangedSchedule).isPresent();
        assertThat(unchangedSchedule.get().locationId()).isEqualTo(originalLocation.id());
        assertThat(unchangedSchedule.get().recurrenceRule()).isEqualTo(schedule.recurrenceRule());
    }

    @Test
    void findByScheduleIdAndStatusNot_ShouldExcludeCancelledOccurrences() {
        // Critère d'acceptation LL-11005 « les recherches utilisent l'état effectif de
        // l'occurrence ».
        Schedule schedule = testSchedule(null);
        Occurrence scheduled = occurrenceRepository.save(
                occurrenceFor(schedule.id(), null, Instant.parse("2026-09-08T09:30:00Z")));
        Occurrence cancelled = occurrenceRepository.save(new Occurrence(
                null, schedule.id(), Instant.parse("2026-09-15T09:30:00Z"),
                Instant.parse("2026-09-15T12:00:00Z"), null, "CANCELLED", true));

        List<Occurrence> effective = occurrenceRepository.findByScheduleIdAndStatusNot(schedule.id(), "CANCELLED");

        assertThat(effective).extracting(Occurrence::id).contains(scheduled.id());
        assertThat(effective).extracting(Occurrence::id).doesNotContain(cancelled.id());
    }
}
