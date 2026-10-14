package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.activityclassification.application.ClassificationService;
import com.locallife.backend.collector.application.DeduplicationService;
import com.locallife.backend.collector.application.ImportFingerprintService;
import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import com.locallife.backend.collector.application.NormalizationService;
import com.locallife.backend.collector.application.SourceActivationService;
import com.locallife.backend.collector.application.SourceDryRunReport;
import com.locallife.backend.collector.application.SourceDryRunService;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.source.application.SourceDefaultCategoryService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Risque d'archivage massif : le vrai {@link DataTourismeCollector} (réponse HTTP simulée) branché sur
 * {@link ImportService} (dépendances simulées). Un fichier invalide ne doit jamais archiver les activités
 * déjà importées de la source ; un fichier valide sans événement retenu archive normalement, sous le
 * garde-fou d'{@code ImportService}.
 */
@ExtendWith(MockitoExtension.class)
class DataTourismeImportSafetyTest {

    private static final String URL =
            "https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80";

    private static final Source SOURCE = new Source(
            20L, "DATAtourisme PACA", "DATATOURISME", URL, "ACTIVE", null, null, "84");

    private static final String HEADER = "Nom_du_POI,Categories_de_POI,Latitude,Longitude,Adresse_postale,"
            + "Code_postal_et_commune,Periodes_regroupees,Covid19_mesures_specifiques,Createur_de_la_donnee,"
            + "SIT_diffuseur,Date_de_mise_a_jour,Contacts_du_POI,Classements_du_POI,Description,URI_ID_du_POI\n";

    /** Événement valide, mais dans les Bouches-du-Rhône : hors du filtre « 84 » de la source. */
    private static final String EVENT_OUTSIDE_FILTER = "Concert,https://www.datatourisme.fr/ontology/core#"
            + "EntertainmentAndEvent,43.29,5.37,1 rue de Rome,13001#Marseille,2099-11-01<->2099-11-01,,OT,SIT,"
            + "2026-10-01,#https://example.org/,,Un concert,https://data.datatourisme.fr/13/concert\n";

    @Mock private SourceService sourceService;
    @Mock private OpenAgendaCollectorFactory openAgendaCollectorFactory;
    @Mock private DataTourismeCollectorFactory dataTourismeCollectorFactory;
    @Mock private NormalizationService normalizationService;
    @Mock private DeduplicationService deduplicationService;
    @Mock private ActivityRepository activityRepository;
    @Mock private LocationService locationService;
    @Mock private ScheduleService scheduleService;
    @Mock private OccurrenceService occurrenceService;
    @Mock private MediaService mediaService;
    @Mock private ActivityMediaService activityMediaService;
    @Mock private ActivityLinkService activityLinkService;
    @Mock private AccessibilityService accessibilityService;
    @Mock private ActivityTagService activityTagService;
    @Mock private ActivityMetadataService activityMetadataService;
    @Mock private ClassificationService classificationService;
    @Mock private ActivityClassificationService activityClassificationService;
    @Mock private ImportFingerprintService importFingerprintService;
    @Mock private SourceDefaultCategoryService sourceDefaultCategoryService;
    @Mock private ActivityCoordinatePrecisionRepository activityCoordinatePrecisionRepository;

    /** Importe {@link #SOURCE} avec le vrai collecteur, qui reçoit {@code body}. */
    private ImportResult importWithFile(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));
        when(sourceService.getAllSources()).thenReturn(List.of(SOURCE));
        when(dataTourismeCollectorFactory.create(SOURCE))
                .thenReturn(new DataTourismeCollector(builder, URL, SOURCE.name(), SOURCE.regionFilter()));
        lenient().when(importFingerprintService.findBySourceId(any())).thenReturn(Map.of());
        lenient().when(classificationService.referentialVersion()).thenReturn("referentiel-1");
        ImportService importService = new ImportService(
                sourceService, openAgendaCollectorFactory, dataTourismeCollectorFactory, normalizationService,
                deduplicationService, activityRepository, locationService, scheduleService, occurrenceService,
                mediaService, activityMediaService, activityLinkService, accessibilityService, activityTagService,
                activityMetadataService, classificationService, activityClassificationService,
                importFingerprintService, sourceDefaultCategoryService, activityCoordinatePrecisionRepository);
        return importService.importAll().get(0);
    }

    private static List<Activity> upcomingImportedActivities(int count) {
        List<Activity> activities = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            activities.add(new Activity(
                    id, "Événement " + id, "description", "concert", 43.95, 4.81,
                    LocalDateTime.now().plusDays(5), null, "PUBLISHED", SOURCE.id(), "external:DATAtourisme:" + id,
                    "https://example.org/", null, null, null, null, null, null, null, null));
        }
        return activities;
    }

    private void assertNothingArchived(ImportResult result) {
        assertEquals(1, result.errors());
        assertEquals(0, result.archived());
        // Le bilan dit pourquoi l'import a été bloqué, avec la raison donnée par le collecteur.
        assertTrue(result.blockedReason().startsWith("Collecte en échec"));
        assertTrue(result.blockedReason().length() > "Collecte en échec".length() + 40);
        verify(activityRepository, never()).findBySourceId(any());
        verify(activityRepository, never()).save(any());
        verify(sourceService, never()).recordSync(any(), any());
    }

    @Test
    void invalidFiles_ShouldNeverArchiveExistingActivities() {
        assertNothingArchived(importWithFile(""));
    }

    @Test
    void headerOnly_ShouldNeverArchiveExistingActivities() {
        assertNothingArchived(importWithFile(HEADER));
    }

    @Test
    void missingColumn_ShouldNeverArchiveExistingActivities() {
        assertNothingArchived(importWithFile(HEADER.replace("URI_ID_du_POI", "URI") + EVENT_OUTSIDE_FILTER));
    }

    @Test
    void errorPage_ShouldNeverArchiveExistingActivities() {
        assertNothingArchived(importWithFile("<html><body>Service indisponible</body></html>\n"));
    }

    /** Fichier valide, aucun événement dans le Vaucluse : import vide légitime, une disparition isolée est archivée. */
    @Test
    void validEmptyFile_ShouldArchiveActivityThatLeftTheSource() {
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(upcomingImportedActivities(1));

        ImportResult result = importWithFile(HEADER + EVENT_OUTSIDE_FILTER);

        assertEquals(0, result.errors());
        assertEquals(1, result.archived());
        verify(activityRepository).save(argThat(activity -> "ARCHIVED".equals(activity.status())));
    }

    /** Fichier valide mais vidé de tous les événements à venir de la source : archivage suspendu. */
    @Test
    void validEmptyFile_ShouldWithholdMassArchiving() {
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(upcomingImportedActivities(12));

        ImportResult result = importWithFile(HEADER + EVENT_OUTSIDE_FILTER);

        assertEquals(0, result.archived());
        assertEquals(12, result.archiveWithheld());
        assertTrue(result.blockedReason().startsWith("Archivage suspendu : 12 activités à venir sur 12"));
        verify(activityRepository, never()).save(any());
    }

    /** Activation vérifiée avec le vrai collecteur, qui reçoit {@code body}. */
    private SourceDryRunReport activateWithFile(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));
        Source inactive = new Source(SOURCE.id(), SOURCE.name(), SOURCE.type(), URL, "INACTIVE", null, null, null);
        when(sourceService.getSourceById(SOURCE.id())).thenReturn(Optional.of(inactive));
        when(dataTourismeCollectorFactory.create(inactive))
                .thenReturn(new DataTourismeCollector(builder, URL, SOURCE.name(), null));
        SourceDryRunService dryRun = new SourceDryRunService(sourceService, openAgendaCollectorFactory,
                dataTourismeCollectorFactory, normalizationService, deduplicationService, activityRepository);
        return new SourceActivationService(dryRun, sourceService).activate(SOURCE.id()).orElseThrow();
    }

    @Test
    void activation_ShouldBeRefused_WhenFileIsInvalid() {
        SourceDryRunReport report = activateWithFile(HEADER);

        assertFalse(report.activated());
        assertEquals(SourceDryRunReport.Verdict.FAILED, report.verdict());
        verify(sourceService, never()).activateAfterVerification(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void activation_ShouldSucceed_WhenFileIsValid() {
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());
        when(deduplicationService.computeDeduplicationKey(any())).thenReturn("external:concert");
        when(normalizationService.normalize(any()))
                .thenReturn(Optional.of(upcomingImportedActivities(1).get(0)));

        SourceDryRunReport report = activateWithFile(HEADER + EVENT_OUTSIDE_FILTER);

        assertTrue(report.activated());
        assertEquals(1, report.collected());
        verify(sourceService).activateAfterVerification(SOURCE.id());
        verify(activityRepository, never()).save(any());
    }
}
