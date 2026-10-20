package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.application.SourceDryRunReport.Verdict;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.DataTourismeCollectorFactory;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Essai à blanc et activation vérifiée : verdicts {@code OK}/{@code FAILED}/{@code SUSPICIOUS}, activation
 * seulement sur {@code OK}, et aucune écriture dans tous les cas.
 */
@ExtendWith(MockitoExtension.class)
class SourceDryRunServiceTest {

    private static final String URL = "https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80";

    private static final Source SOURCE = new Source(
            20L, "DATAtourisme PACA", "DATATOURISME", URL, "INACTIVE", null, null, "84");

    @Mock private SourceService sourceService;
    @Mock private OpenAgendaCollectorFactory openAgendaCollectorFactory;
    @Mock private DataTourismeCollectorFactory dataTourismeCollectorFactory;
    @Mock private NormalizationService normalizationService;
    @Mock private DeduplicationService deduplicationService;
    @Mock private ActivityRepository activityRepository;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private Collector collector;

    private SourceDryRunService dryRunService;
    private SourceActivationService activationService;

    @BeforeEach
    void setUp() {
        dryRunService = new SourceDryRunService(sourceService, openAgendaCollectorFactory,
                dataTourismeCollectorFactory, normalizationService, deduplicationService, activityRepository);
        activationService = new SourceActivationService(dryRunService, sourceService);
        lenient().when(sourceService.getSourceById(SOURCE.id())).thenReturn(Optional.of(SOURCE));
        lenient().when(dataTourismeCollectorFactory.create(any())).thenReturn(collector);
        lenient().when(deduplicationService.computeDeduplicationKey(any()))
                .thenAnswer(invocation -> "external:" + ((CollectedActivity) invocation.getArgument(0)).externalId());
    }

    private static CollectedActivity item(String externalId) {
        return new CollectedActivity(
                "Concert " + externalId, "description", "concert", "https://example.org", externalId,
                SOURCE.name(), null, null, null, null,
                new CollectedLocation(null, null, "Avignon", "84000", null, "FR", 43.95, 4.81, "Europe/Paris"),
                List.of(new CollectedTiming(LocalDateTime.now().plusDays(3), null)),
                null, List.of(), List.of(), null, List.of(), null);
    }

    private static Activity imported(String externalId, LocalDateTime start) {
        return new Activity(
                null, "Concert " + externalId, "description", "concert", 43.95, 4.81, start, null, "PUBLISHED",
                SOURCE.id(), "external:" + externalId, "https://example.org",
                null, null, null, null, null, null, null, null);
    }

    private void normalizationAccepts(boolean valid) {
        lenient().when(normalizationService.normalize(any()))
                .thenReturn(valid ? Optional.of(imported("x", LocalDateTime.now())) : Optional.empty());
    }

    /** Aucune écriture : ni activité, ni source (activation, synchronisation). */
    private void assertNothingWritten() {
        verify(activityRepository, never()).save(any());
        verify(sourceService, never()).recordSync(any(), any());
        verify(sourceService, never()).activateAfterVerification(any());
    }

    @Test
    void dryRun_ShouldBeEmpty_WhenSourceIsUnknown() {
        when(sourceService.getSourceById(99L)).thenReturn(Optional.empty());

        assertTrue(dryRunService.dryRun(99L).isEmpty());
    }

    @Test
    void dryRun_ShouldFail_WithoutCollecting_WhenConfigurationIsInvalid() {
        Source badUrl = new Source(21L, "DT", "DATATOURISME", "https://example.org/file.csv", "INACTIVE", null,
                null, "84");
        when(sourceService.getSourceById(21L)).thenReturn(Optional.of(badUrl));

        SourceDryRunReport report = dryRunService.dryRun(21L).orElseThrow();

        assertEquals(Verdict.FAILED, report.verdict());
        assertTrue(report.problems().get(0).startsWith("Configuration invalide"));
        verify(dataTourismeCollectorFactory, never()).create(any());
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldFail_WhenOpenAgendaSourceHasNoAgenda() {
        Source noAgenda = new Source(22L, "OA", "API", null, "INACTIVE", null, " ", null);
        when(sourceService.getSourceById(22L)).thenReturn(Optional.of(noAgenda));

        assertEquals(Verdict.FAILED, dryRunService.dryRun(22L).orElseThrow().verdict());
        verify(openAgendaCollectorFactory, never()).create(any());
    }

    @Test
    void dryRun_ShouldFail_WhenCollectionFails() {
        when(collector.collect()).thenThrow(new CollectorException("Fichier DATAtourisme vide", null));

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.FAILED, report.verdict());
        assertTrue(report.problems().get(0).contains("Fichier DATAtourisme vide"));
        verify(activityRepository, never()).findBySourceId(any());
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldNotDisplayUnexpectedExceptionMessage_WhichMayContainApiKey() {
        when(collector.collect()).thenThrow(new IllegalStateException("GET https://api.example/events?key=SECRET"));

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.FAILED, report.verdict());
        assertFalse(report.problems().get(0).contains("SECRET"));
        assertTrue(report.problems().get(0).contains("IllegalStateException"));
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldBeSuspicious_WhenNothingIsCollected() {
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.SUSPICIOUS, report.verdict());
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldBeSuspicious_WhenMostItemsAreRejectedByNormalization() {
        when(collector.collect()).thenReturn(List.of(item("1"), item("2"), item("3")));
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());
        normalizationAccepts(false);

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.SUSPICIOUS, report.verdict());
        assertEquals(3, report.invalid());
        assertNothingWritten();
    }

    /** Limite exacte : la moitié des éléments rejetés n'est pas suspecte, plus de la moitié l'est. */
    @Test
    void dryRun_ShouldApplyInvalidRatio_AtItsBoundary() {
        when(collector.collect()).thenReturn(List.of(item("1"), item("2"), item("3"), item("4")));
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());
        Activity valid = imported("x", LocalDateTime.now());
        when(normalizationService.normalize(any()))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(valid), Optional.of(valid))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(valid));

        SourceDryRunReport half = dryRunService.dryRun(SOURCE.id()).orElseThrow();
        SourceDryRunReport threeQuarters = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(0.5, SourceDryRunService.MAX_INVALID_RATIO);
        assertEquals(2, half.invalid());
        assertEquals(Verdict.OK, half.verdict());
        assertEquals(3, threeQuarters.invalid());
        assertEquals(Verdict.SUSPICIOUS, threeQuarters.verdict());
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldBeSuspicious_WhenUpcomingActivitiesWouldMassivelyDisappear() {
        when(collector.collect()).thenReturn(List.of(item("nouveau")));
        List<Activity> existing = new ArrayList<>();
        for (int i = 0; i < ImportService.MASS_ARCHIVE_MIN_COUNT; i++) {
            existing.add(imported("ancien-" + i, LocalDateTime.now().plusDays(5)));
        }
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(existing);
        normalizationAccepts(true);

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.SUSPICIOUS, report.verdict());
        assertEquals(ImportService.MASS_ARCHIVE_MIN_COUNT, report.archiveWithheld());
        assertEquals(0, report.wouldArchive());
        assertNothingWritten();
    }

    @Test
    void dryRun_ShouldReportWhatAnImportWouldDo_WithoutWritingAnything() {
        when(collector.collect()).thenReturn(List.of(item("deja-la"), item("nouveau")));
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of(
                imported("deja-la", LocalDateTime.now().plusDays(3)),
                imported("disparu", LocalDateTime.now().plusDays(4))));
        normalizationAccepts(true);

        SourceDryRunReport report = dryRunService.dryRun(SOURCE.id()).orElseThrow();

        assertEquals(Verdict.OK, report.verdict());
        assertEquals(2, report.collected());
        assertEquals(1, report.wouldCreate());
        assertEquals(1, report.wouldUpdate());
        assertEquals(1, report.wouldArchive());
        assertEquals(List.of("Concert deja-la", "Concert nouveau"), report.sampleTitles());
        assertFalse(report.activated());
        verify(activityRepository).findBySourceId(SOURCE.id());
        verifyNoMoreInteractions(activityRepository);
        assertNothingWritten();
    }

    @Test
    void activate_ShouldActivateSource_OnlyWhenDryRunIsOk() {
        when(collector.collect()).thenReturn(List.of(item("1")));
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());
        normalizationAccepts(true);

        SourceDryRunReport report = activationService.activate(SOURCE.id()).orElseThrow();

        assertTrue(report.activated());
        verify(sourceService).activateAfterVerification(SOURCE.id());
    }

    @Test
    void activate_ShouldRefuse_WhenCollectionFails() {
        when(collector.collect()).thenThrow(new CollectorException("Réponse HTTP 404", null));

        SourceDryRunReport report = activationService.activate(SOURCE.id()).orElseThrow();

        assertFalse(report.activated());
        assertEquals(Verdict.FAILED, report.verdict());
        assertNothingWritten();
    }

    @Test
    void activate_ShouldRefuse_WhenResultIsSuspicious() {
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(SOURCE.id())).thenReturn(List.of());

        SourceDryRunReport report = activationService.activate(SOURCE.id()).orElseThrow();

        assertFalse(report.activated());
        assertEquals(Verdict.SUSPICIOUS, report.verdict());
        assertNothingWritten();
    }
}
