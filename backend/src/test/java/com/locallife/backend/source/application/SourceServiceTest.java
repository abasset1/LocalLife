package com.locallife.backend.source.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private SourceService sourceService;

    @Test
    void createSource_ShouldSaveAndReturnSource_WithActiveStatusAndNoLastSync() {
        // Given
        Source saved = new Source(
                1L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null,
                "agenda-uid", "PACA");
        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        // When
        Source result = sourceService.createSource(
                "OpenAgenda Marseille", "API", "https://api.openagenda.com", "agenda-uid", "PACA");

        // Then
        assertEquals(1L, result.id());
        assertEquals("OpenAgenda Marseille", result.name());
        assertEquals("API", result.type());
        assertEquals("ACTIVE", result.status());
        verify(sourceRepository).save(argThat(source ->
                source.id() == null && "ACTIVE".equals(source.status()) && source.lastSyncAt() == null));
    }

    @Test
    void createSource_ShouldThrow_WhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("  ", "API", null, null, null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void createSource_ShouldThrow_WhenTypeIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.createSource("Nom", "", null, null, null));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void getAllSources_ShouldReturnAllSourcesFromRepository() {
        // Given
        List<Source> sources = List.of(
                new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null),
                new Source(2L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null,
                        "agenda-uid", null));
        when(sourceRepository.findAll()).thenReturn(sources);

        // When
        List<Source> result = sourceService.getAllSources();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getSourceById_ShouldReturnSource_WhenFound() {
        // Given
        Source source = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(source));

        // When
        Optional<Source> result = sourceService.getSourceById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Saisie manuelle", result.get().name());
    }

    @Test
    void getSourceById_ShouldReturnEmpty_WhenNotFound() {
        // Given
        when(sourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.getSourceById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findByType_ShouldDelegateToRepository() {
        // Given
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));

        // When
        Optional<Source> result = sourceService.findByType("MANUAL");

        // Then
        assertTrue(result.isPresent());
        assertEquals("Saisie manuelle", result.get().name());
    }

    @Test
    void updateSource_ShouldSaveAndReturnUpdatedSource_PreservingLastSyncAt() {
        // Given
        LocalDateTime lastSyncAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        Source existing = new Source(1L, "Ancien nom", "API", null, "ACTIVE", lastSyncAt, "old-uid", null);
        Source saved = new Source(1L, "Nouveau nom", "API", "https://x", "INACTIVE", lastSyncAt, "new-uid", "PACA");
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        // When
        Optional<Source> result = sourceService.updateSource(
                1L, "Nouveau nom", "API", "https://x", "INACTIVE", "new-uid", "PACA");

        // Then
        assertTrue(result.isPresent());
        assertEquals("Nouveau nom", result.get().name());
        verify(sourceRepository).save(argThat(source -> lastSyncAt.equals(source.lastSyncAt())));
    }

    @Test
    void updateSource_ShouldReturnEmpty_WhenSourceDoesNotExist() {
        // Given
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.updateSource(99L, "Nom", "API", null, "ACTIVE", null, null);

        // Then
        assertFalse(result.isPresent());
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void updateSource_ShouldThrow_WhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> sourceService.updateSource(1L, "", "API", null, "ACTIVE", null, null));
        verify(sourceRepository, never()).findById(any());
    }

    @Test
    void deleteSource_ShouldDeleteAndReturnSource_WhenNotReservedManual() {
        // Given
        Source source = new Source(2L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid", null);
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(source));
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));
        when(activityRepository.findBySourceId(2L)).thenReturn(List.of());

        // When
        Optional<Source> result = sourceService.deleteSource(2L);

        // Then
        assertTrue(result.isPresent());
        verify(sourceRepository).deleteById(2L);
    }

    @Test
    void deleteSource_ShouldDetachLinkedActivities_ToReservedManualSource() {
        // Given : décision Alex (LL-EF-005) : suppression autorisée, activités détachées
        // vers la source réservée MANUAL plutôt que la suppression bloquée.
        Source source = new Source(2L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid", null);
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        Activity linkedActivity = new Activity(
                42L, "Marché de Noël", "description", "marché", 43.2965, 5.3698,
                LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED", 2L, "external:key", null);
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(source));
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));
        when(activityRepository.findBySourceId(2L)).thenReturn(List.of(linkedActivity));

        // When
        sourceService.deleteSource(2L);

        // Then
        verify(activityRepository).save(argThat(activity ->
                activity.id().equals(42L) && activity.sourceId().equals(1L)));
        verify(sourceRepository).deleteById(2L);
    }

    @Test
    void deleteSource_ShouldThrow_WhenSourceIsReservedManual() {
        // Given
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(manualSource));

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> sourceService.deleteSource(1L));
        verify(sourceRepository, never()).deleteById(any());
        verify(activityRepository, never()).findBySourceId(any());
    }

    @Test
    void deleteSource_ShouldReturnEmpty_WhenSourceDoesNotExist() {
        // Given
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<Source> result = sourceService.deleteSource(99L);

        // Then
        assertFalse(result.isPresent());
        verify(sourceRepository, never()).deleteById(any());
    }

}
