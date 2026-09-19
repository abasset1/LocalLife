package com.locallife.backend.activity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.accessibility.infrastructure.AccessibilityRepository;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.link.infrastructure.ActivityLinkRepository;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.media.infrastructure.ActivityMediaRepository;
import com.locallife.backend.media.infrastructure.MediaRepository;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.place.infrastructure.LocationRepository;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import com.locallife.backend.tag.domain.ActivityTag;
import com.locallife.backend.tag.infrastructure.ActivityTagRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * LL-11016 (« Tester les cas réels de programmation ») : construit
 * effectivement, via le modèle événementiel riche du Sprint 11
 * (`Location`/`Schedule`/`Occurrence`/`Media`/`ActivityLink`/
 * `Accessibility`/`ActivityTag`), chacun des cinq scénarios décrits par
 * le ticket (`docs/05_Sprints/SPRINT_11.md`, section 19), puis vérifie
 * — via une vraie requête HTTP sur `GET /api/v1/activities/{id}` — que
 * `ActivityDetailResponse` (LL-11013) les représente correctement. Le
 * critère d'acceptation du ticket (« tous ces scénarios doivent être
 * représentables et testés ») est pris au pied de la lettre : chaque cas
 * est un test à part entière, pas une simple relecture manuelle.
 *
 * A révélé, en construisant le « Cas 4 — Exception », que
 * `ActivityController#toDetailResponse` (LL-11013) filtrait les
 * occurrences annulées (« pertinentes » interprété comme « non
 * annulées ») — corrigé par ce même ticket (voir sa javadoc et
 * `docs/ADR-0010-cas-reels-programmation.md`) : une occurrence annulée
 * doit au contraire rester visible dans la fiche, pas en être absente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealSchedulingScenariosIntegrationTest {

    @LocalServerPort
    private int port;

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

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ActivityMediaRepository activityMediaRepository;

    @Autowired
    private ActivityLinkRepository activityLinkRepository;

    @Autowired
    private AccessibilityRepository accessibilityRepository;

    @Autowired
    private ActivityTagRepository activityTagRepository;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /**
     * Type {@code "API"} plutôt que {@code "MANUAL"} : ce dernier est
     * réservé à l'unique source créée par {@code
     * V8__create_source_table.sql} (contrainte {@code
     * V30__enforce_single_manual_source.sql}, bug corrigé après le
     * premier run CI de ce ticket, voir l'addendum de
     * {@code docs/ADR-0010-cas-reels-programmation.md}) — une source de
     * test ordinaire, quel que soit son usage réel dans le test, doit
     * utiliser un type non réservé.
     */
    private Long testSourceId() {
        Source source = new Source(
                null, "Source test LL-11016-" + UUID.randomUUID(), "API", null, "ACTIVE", null, null, null);
        return sourceRepository.save(source).id();
    }

    private Activity persistActivity(String title, double latitude, double longitude, LocalDateTime startDate) {
        Activity activity = new Activity(
                null, title, "Description", "test", latitude, longitude, startDate, null, "PUBLISHED",
                testSourceId(), null, null, null, null, null, null, null, null, null, null);
        return activityRepository.save(activity);
    }

    private Location persistLocation(String city, double latitude, double longitude) {
        return locationRepository.save(new Location(
                null, null, null, null, city, null, null, null, null, latitude, longitude, null, null, null, null));
    }

    private Schedule persistSchedule(Long activityId, Long locationId, String recurrenceRule) {
        return scheduleRepository.save(new Schedule(
                null, activityId, locationId, null, null, null, null, null, recurrenceRule));
    }

    private Occurrence persistOccurrence(
            Long scheduleId, LocalDateTime startAt, LocalDateTime endAt, Long locationId, String status) {
        return occurrenceRepository.save(new Occurrence(
                null, scheduleId,
                startAt.toInstant(ZoneOffset.UTC),
                endAt == null ? null : endAt.toInstant(ZoneOffset.UTC),
                locationId, status, false));
    }

    @Test
    void cas1_EvenementPonctuel_ShouldBeRepresentedCorrectly() {
        Activity activity = persistActivity(
                "Concert", 43.9493, 4.8055, LocalDateTime.of(2026, 9, 12, 20, 0));
        Location avignon = persistLocation("Avignon", 43.9493, 4.8055);
        Schedule schedule = persistSchedule(activity.id(), avignon.id(), null);
        persistOccurrence(
                schedule.id(), LocalDateTime.of(2026, 9, 12, 20, 0), LocalDateTime.of(2026, 9, 12, 23, 0),
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);

        restTestClient().get().uri("/api/v1/activities/" + activity.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Concert")
                .jsonPath("$.schedules.length()").isEqualTo(1)
                .jsonPath("$.occurrences.length()").isEqualTo(1)
                .jsonPath("$.occurrences[0].status").isEqualTo("SCHEDULED")
                .jsonPath("$.locations[0].city").isEqualTo("Avignon");
    }

    @Test
    void cas2_EvenementRecurrent_ShouldBeRepresentedCorrectly() {
        Activity activity = persistActivity("Marché", 43.9493, 4.8055, LocalDateTime.of(2026, 9, 1, 8, 0));
        Location avignon = persistLocation("Avignon", 43.9493, 4.8055);
        Schedule schedule = persistSchedule(activity.id(), avignon.id(), "FREQ=WEEKLY;BYDAY=TU");
        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 1, 8, 0), LocalDateTime.of(2026, 9, 1, 13, 0),
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);
        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 8, 8, 0), LocalDateTime.of(2026, 9, 8, 13, 0),
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);

        restTestClient().get().uri("/api/v1/activities/" + activity.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.schedules[0].recurrenceRule").isEqualTo("FREQ=WEEKLY;BYDAY=TU")
                .jsonPath("$.occurrences.length()").isEqualTo(2)
                .jsonPath("$.locations.length()").isEqualTo(1);
    }

    @Test
    void cas3_FoodTruckMultiLieux_ShouldBeRepresentedCorrectly() {
        Activity activity = persistActivity("Food truck", 43.2965, 5.3698, LocalDateTime.of(2026, 9, 7, 12, 0));
        Location marseille = persistLocation("Marseille", 43.2965, 5.3698);
        Location avignon = persistLocation("Avignon", 43.9493, 4.8055);
        Location orange = persistLocation("Orange", 44.1358, 4.8078);

        Schedule scheduleLundi = persistSchedule(activity.id(), marseille.id(), "FREQ=WEEKLY;BYDAY=MO");
        Schedule scheduleMardi = persistSchedule(activity.id(), avignon.id(), "FREQ=WEEKLY;BYDAY=TU");
        Schedule scheduleMercredi = persistSchedule(activity.id(), orange.id(), "FREQ=WEEKLY;BYDAY=WE");
        persistOccurrence(scheduleLundi.id(), LocalDateTime.of(2026, 9, 7, 12, 0), null,
                marseille.id(), OccurrenceService.STATUS_SCHEDULED);
        persistOccurrence(scheduleMardi.id(), LocalDateTime.of(2026, 9, 8, 12, 0), null,
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);
        persistOccurrence(scheduleMercredi.id(), LocalDateTime.of(2026, 9, 9, 12, 0), null,
                orange.id(), OccurrenceService.STATUS_SCHEDULED);

        restTestClient().get().uri("/api/v1/activities/" + activity.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.schedules.length()").isEqualTo(3)
                .jsonPath("$.locations.length()").isEqualTo(3);
    }

    /**
     * LL-11016, « Cas 4 — Exception » : une série hebdomadaire (tous les
     * mardis, à Avignon) avec trois occurrences concrètes — l'une
     * annulée (15/09), l'une déplacée à Marseille (22/09), l'une
     * normale (29/09). Voir la javadoc de la classe : c'est ce cas
     * précis qui a révélé le bug corrigé par ce ticket dans {@code
     * ActivityController#toDetailResponse}.
     */
    @Test
    void cas4_Exception_ShouldBeRepresentedCorrectly() {
        Activity activity = persistActivity(
                "Tous les mardis", 43.9493, 4.8055, LocalDateTime.of(2026, 9, 15, 18, 0));
        Location avignon = persistLocation("Avignon", 43.9493, 4.8055);
        Location marseille = persistLocation("Marseille", 43.2965, 5.3698);
        Schedule schedule = persistSchedule(activity.id(), avignon.id(), "FREQ=WEEKLY;BYDAY=TU");

        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 15, 18, 0), null,
                avignon.id(), OccurrenceService.STATUS_CANCELLED);
        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 22, 18, 0), null,
                marseille.id(), OccurrenceService.STATUS_SCHEDULED);
        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 29, 18, 0), null,
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);

        List<ActivityDetailResponse.OccurrenceSummary> occurrences = restTestClient().get()
                .uri("/api/v1/activities/" + activity.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActivityDetailResponse.class)
                .returnResult()
                .getResponseBody()
                .occurrences();

        assertEquals(3, occurrences.size());
        assertTrue(occurrences.stream().anyMatch(o -> "CANCELLED".equals(o.status())));
        assertTrue(occurrences.stream()
                .anyMatch(o -> "SCHEDULED".equals(o.status()) && marseille.id().equals(o.locationId())));
        assertTrue(occurrences.stream()
                .anyMatch(o -> "SCHEDULED".equals(o.status()) && avignon.id().equals(o.locationId())));
    }

    @Test
    void cas5_FicheRiche_ShouldBeRepresentedCorrectly() {
        Activity activity = activityRepository.save(new Activity(
                null, "Festival", "Description courte", "festival", 43.9493, 4.8055,
                LocalDateTime.of(2026, 9, 20, 19, 0), LocalDateTime.of(2026, 9, 20, 23, 0), "PUBLISHED",
                testSourceId(), null, "https://example.com", "Place de l'Horloge", "Avignon", "84000",
                "Description longue et détaillée du festival, sur plusieurs phrases.",
                "Gratuit, tout public.", 0, null, null));
        Location avignon = persistLocation("Avignon", 43.9493, 4.8055);
        Schedule schedule = persistSchedule(activity.id(), avignon.id(), null);
        persistOccurrence(schedule.id(), LocalDateTime.of(2026, 9, 20, 19, 0), LocalDateTime.of(2026, 9, 20, 23, 0),
                avignon.id(), OccurrenceService.STATUS_SCHEDULED);

        Media media = mediaRepository.save(new Media(
                null, "https://example.com/photo.jpg", "image", 800, 600, "Ville d'Avignon", "Le festival"));
        activityMediaRepository.save(new ActivityMedia(null, activity.id(), media.id(), 0));
        activityLinkRepository.save(new ActivityLink(
                null, activity.id(), "WEBSITE", "Site officiel", "https://example.com/festival"));
        accessibilityRepository.save(new Accessibility(null, activity.id(), true, null, null, null, null));
        activityTagRepository.save(new ActivityTag(null, activity.id(), "festival"));
        activityTagRepository.save(new ActivityTag(null, activity.id(), "musique"));

        restTestClient().get().uri("/api/v1/activities/" + activity.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Festival")
                .jsonPath("$.description").isEqualTo("Description courte")
                .jsonPath("$.longDescription").exists()
                .jsonPath("$.media.length()").isEqualTo(1)
                .jsonPath("$.locations.length()").isEqualTo(1)
                .jsonPath("$.schedules.length()").isEqualTo(1)
                .jsonPath("$.links.length()").isEqualTo(1)
                .jsonPath("$.accessibility.motorImpairment").isEqualTo(true)
                .jsonPath("$.tags.length()").isEqualTo(2);
    }
}
