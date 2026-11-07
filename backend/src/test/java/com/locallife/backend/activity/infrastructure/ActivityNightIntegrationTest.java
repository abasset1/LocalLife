package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carte « Night » : filtre {@code night} de {@link ActivityRepository#findWithinRadius} et
 * {@link ActivityRepository#findWithinBounds} (fonction SQL {@code ll_is_night}, V88), contre la base réelle.
 * Toutes les occurrences sont placées demain (dans la fenêtre par défaut de 7 jours), en heure de Paris.
 */
@SpringBootTest
@Transactional
class ActivityNightIntegrationTest {

    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");
    private static final double LAT = 43.2951;
    private static final double LON = 5.3739;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    private final LocalDate tomorrow = LocalDate.now(PARIS_ZONE).plusDays(1);

    private LocalDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute);
    }

    private Long manualSourceId() {
        return sourceRepository.findByType("MANUAL").map(Source::id).orElseThrow();
    }

    private Activity activity(LocalDateTime startDate, LocalDateTime endDate) {
        return activityRepository.save(new Activity(
                null, "night-" + UUID.randomUUID(), "description", "concert", LAT, LON, startDate, endDate,
                "PUBLISHED", manualSourceId(), null, null, null, null, null, null, null, null, null, null));
    }

    /** Activité programmée : une occurrence au lieu de Marseille, de {@code start} à {@code end} (heure de Paris). */
    private Activity scheduled(LocalDateTime start, LocalDateTime end) {
        Activity activity = activity(LocalDateTime.now(PARIS_ZONE), null);
        Location location = locationRepository.save(new Location(
                null, "Lieu " + UUID.randomUUID(), null, null, "Marseille", null, null, null, null,
                LAT, LON, null, null, null, null));
        Schedule schedule = scheduleRepository.save(new Schedule(
                null, activity.id(), location.id(), null, null, null, null, null, null));
        occurrenceRepository.save(new Occurrence(
                null, schedule.id(), start.atZone(PARIS_ZONE).toInstant(),
                end == null ? null : end.atZone(PARIS_ZONE).toInstant(), location.id(), "SCHEDULED", false));
        return activity;
    }

    private List<Long> radius(boolean night) {
        return activityRepository.findWithinRadius(LAT, LON, 1000, "PUBLISHED", null, null, null, night)
                .stream().map(Activity::id).toList();
    }

    private List<Long> bounds(boolean night) {
        return activityRepository.findWithinBounds(
                        LAT - 0.01, LON - 0.01, LAT + 0.01, LON + 0.01, "PUBLISHED", null, null, null, night)
                .stream().map(Activity::id).toList();
    }

    @Test
    void night_ShouldKeepEveningAndLateNightOccurrences_AndDropDaytimeOnes() {
        Activity evening = scheduled(at(tomorrow, 21, 0), at(tomorrow, 23, 30));
        Activity afterMidnight = scheduled(at(tomorrow, 2, 0), null);
        Activity endsAtNight = scheduled(at(tomorrow, 18, 0), at(tomorrow, 22, 0));
        Activity crossesMidnight = scheduled(at(tomorrow, 19, 0), at(tomorrow.plusDays(1), 4, 0));
        Activity daytime = scheduled(at(tomorrow, 10, 0), at(tomorrow, 18, 0));
        Activity allDay = scheduled(tomorrow.atStartOfDay(), tomorrow.atTime(LocalTime.of(23, 59, 59)));
        Activity exhibition = scheduled(at(tomorrow, 10, 0), at(tomorrow.plusDays(30), 18, 0));

        for (List<Long> night : List.of(radius(true), bounds(true))) {
            assertThat(night).contains(evening.id(), afterMidnight.id(), endsAtNight.id(), crossesMidnight.id())
                    .doesNotContain(daytime.id(), allDay.id(), exhibition.id());
        }

        assertThat(radius(false)).contains(evening.id(), daytime.id(), allDay.id(), exhibition.id());
    }

    @Test
    void night_ShouldUseActivityDates_WhenActivityHasNoSchedule() {
        Activity party = activity(at(tomorrow, 22, 30), null);
        Activity market = activity(at(tomorrow, 9, 0), at(tomorrow, 13, 0));

        assertThat(radius(true)).contains(party.id()).doesNotContain(market.id());
        assertThat(bounds(true)).contains(party.id()).doesNotContain(market.id());
        assertThat(bounds(false)).contains(party.id(), market.id());
    }
}
