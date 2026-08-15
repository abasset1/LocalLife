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
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private ActivityRepository activityRepository;

    private ImportService importService() {
        return new ImportService(
                List.of(collector), normalizationService, deduplicationService, sourceService, activityRepository);
    }

    private static final Source SOURCE = new Source(10L, "OpenAgenda Marseille", "API", null, "ACTIVE", null);

    private CollectedActivity collectedActivity(String externalId) {
        return new CollectedActivity(
                "Marché de Noël", "description", LocalDateTime.of(2026, 12, 1, 10, 0), null,
                "marché", 43.2965, 5.3698, "https://example.com", externalId, "OpenAgenda Marseille");
    }

    private Activity normalizedActivity() {
        return new Activity(
                null, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED", null, null);
    }

    @Test
    void importAll_ShouldCreateNewActivity_WhenNoExistingMatchFound() {
        // Given
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        Activity existing = new Activity(
                42L, "Marché de Noël", "old description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1");
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
        when(collector.collect()).thenReturn(List.of());
        Activity previouslyImported = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1");
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
        when(collector.collect()).thenReturn(List.of());
        Activity alreadyArchived = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "ARCHIVED",
                10L, "external:OpenAgenda Marseille:ext-1");
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
        when(collector.getSourceName()).thenReturn("OpenAgenda Marseille");
        when(sourceService.findOrCreateByName("OpenAgenda Marseille", "API", null)).thenReturn(SOURCE);
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        importService().importAll();

        // Then
        verify(activityRepository, times(1)).findBySourceId(eq(10L));
    }

}
