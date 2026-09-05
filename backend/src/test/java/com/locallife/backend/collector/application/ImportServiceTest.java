package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LL-EF-005 : {@code ImportService} ne reçoit plus un {@code List<Collector>}
 * fixé au démarrage — il relit {@code sourceService.getAllSources()} à
 * chaque appel et construit un collecteur via
 * {@code OpenAgendaCollectorFactory} pour chaque source collectible. Chaque
 * test stub donc {@code sourceService.getAllSources()} pour renvoyer
 * {@link #SOURCE} (type {@code API}, statut {@code ACTIVE}, un
 * {@code agendaUid} non vide — les trois conditions de
 * {@code ImportService#isCollectible}), et
 * {@code openAgendaCollectorFactory.create(SOURCE)} pour renvoyer le
 * {@code collector} mocké — {@code collector.getSourceName()} n'est plus
 * consulté par {@code ImportService} (le nom vient directement de
 * {@code SOURCE.name()}) et n'a donc plus besoin d'être stubbé.
 */
@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private Collector collector;

    @Mock
    private NormalizationService normalizationService;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private SourceService sourceService;

    @Mock
    private OpenAgendaCollectorFactory openAgendaCollectorFactory;

    @Mock
    private ActivityRepository activityRepository;

    private ImportService importService() {
        return new ImportService(
                sourceService, openAgendaCollectorFactory, normalizationService, deduplicationService,
                activityRepository);
    }

    private static final Source SOURCE = new Source(
            10L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid-marseille", null);

    /** Stub commun à tous les tests : une seule source collectible, {@link #SOURCE}. */
    private void givenOneCollectibleSource() {
        when(sourceService.getAllSources()).thenReturn(List.of(SOURCE));
        when(openAgendaCollectorFactory.create(SOURCE)).thenReturn(collector);
    }

    private CollectedActivity collectedActivity(String externalId) {
        return new CollectedActivity(
                "Marché de Noël", "description", LocalDateTime.of(2026, 12, 1, 10, 0), null,
                "marché", 43.2965, 5.3698, "https://example.com", externalId, "OpenAgenda Marseille");
    }

    private Activity normalizedActivity() {
        return new Activity(
                null, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED", null, null,
                "https://example.com");
    }

    @Test
    void importAll_ShouldCreateNewActivity_WhenNoExistingMatchFound() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(1, results.size());
        ImportResult result = results.get(0);
        assertEquals("OpenAgenda Marseille", result.sourceName());
        assertEquals(1, result.fetched());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.ignored());
        assertEquals(0, result.errors());
        assertEquals(0, result.archived());
        assertFalse(result.startedAt().isAfter(result.endedAt()));
        verify(activityRepository).save(argThatMatchesNewActivity());
    }

    private Activity argThatMatchesNewActivity() {
        return argThat(activity ->
                activity.id() == null
                        && activity.sourceId().equals(10L)
                        && "external:OpenAgenda Marseille:ext-1".equals(activity.importKey()));
    }

    @Test
    void importAll_ShouldUpdateExistingActivity_WhenMatchFoundForSameSource() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        Activity existing = new Activity(
                42L, "Marché de Noël", "old description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com/old");
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.of(existing));
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(existing));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(activityRepository).save(argThat(activity -> activity.id() != null && activity.id().equals(42L)));
    }

    @Test
    void importAll_ShouldCountIgnored_WhenNormalizationRejectsData() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(1, result.fetched());
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.ignored());
        assertEquals(0, result.errors());
        verify(activityRepository, never()).findBySourceIdAndImportKey(any(), any());
    }

    @Test
    void importAll_ShouldCountError_WhenUnexpectedExceptionThrownForOneItem() {
        // Given : la déduplication échoue de façon inattendue sur cet élément précis.
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenThrow(new RuntimeException("boom"));
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then : l'erreur sur cet élément ne fait pas planter tout l'import.
        ImportResult result = results.get(0);
        assertEquals(1, result.fetched());
        assertEquals(0, result.created());
        assertEquals(0, result.ignored());
        assertEquals(1, result.errors());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void importAll_ShouldReturnDegradedResult_WhenCollectorFailsEntirely() {
        // Given
        givenOneCollectibleSource();
        when(collector.collect()).thenThrow(new CollectorException("panne réseau", null));

        // When
        List<ImportResult> results = importService().importAll();

        // Then : pas d'exception propagée, un résultat dégradé est renvoyé pour cette source.
        assertEquals(1, results.size());
        ImportResult result = results.get(0);
        assertEquals(0, result.fetched());
        assertEquals(0, result.created());
        assertEquals(1, result.errors());
        verify(activityRepository, never()).findBySourceId(any());
    }

    @Test
    void importAll_ShouldArchiveActivity_WhenNoLongerReturnedByCollector() {
        // Given: aucune donnée collectée cette fois, mais une activité existante pour cette source.
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        Activity previouslyImported = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com");
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(previouslyImported));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(1, result.archived());
        verify(activityRepository).save(argThat(activity ->
                activity.id().equals(42L) && "ARCHIVED".equals(activity.status())));
    }

    @Test
    void importAll_ShouldNotReArchive_WhenActivityAlreadyArchived() {
        // Given
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        Activity alreadyArchived = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "ARCHIVED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com");
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(alreadyArchived));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(0, results.get(0).archived());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void importAll_ShouldNeverTouchManualActivities_BecauseScopedToImportedSourceId() {
        // Given : findBySourceId(10L) ne renvoie que les activités de cette source — les
        // activités manuelles (sourceId différent) ne sont jamais dans cette liste, par
        // construction du repository. On vérifie ici que le service interroge bien
        // findBySourceId avec l'id de la source importée, pas une recherche globale.
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        importService().importAll();

        // Then
        verify(activityRepository, times(1)).findBySourceId(eq(10L));
    }

    @Test
    void importAll_ShouldIgnoreNonCollectibleSources() {
        // Given : une source MANUAL (jamais collectible) et une source API sans agendaUid
        // (type API mais pas encore configurée pour la collecte) ne doivent déclencher ni
        // collecte ni construction de collecteur.
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        Source apiSourceWithoutAgendaUid = new Source(2L, "Futur RSS", "API", null, "ACTIVE", null, null, null);
        Source inactiveSource = new Source(3L, "Agenda désactivé", "API", null, "INACTIVE", null, "uid-3", null);
        when(sourceService.getAllSources())
                .thenReturn(List.of(manualSource, apiSourceWithoutAgendaUid, inactiveSource));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(0, results.size());
        verify(openAgendaCollectorFactory, never()).create(any());
    }

}
