package com.locallife.backend.source.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
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

    @InjectMocks
    private SourceService sourceService;

    @Test
    void createSource_ShouldSaveAndReturnSource_WithActiveStatusAndNoLastSync() {
        // Given
        Source saved = new Source(1L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null);
        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        // When
        Source result = sourceService.createSource("OpenAgenda Marseille", "API", "https://api.openagenda.com");

        // Then
        assertEquals(1L, result.id());
        assertEquals("OpenAgenda Marseille", result.name());
        assertEquals("API", result.type());
        assertEquals("ACTIVE", result.status());
        verify(sourceRepository).save(any(Source.class));
    }

    @Test
    void getAllSources_ShouldReturnAllSourcesFromRepository() {
        // Given
        List<Source> sources = List.of(
                new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null),
                new Source(2L, "OpenAgenda Marseille", "API", "https://api.openagenda.com", "ACTIVE", null));
        when(sourceRepository.findAll()).thenReturn(sources);

        // When
        List<Source> result = sourceService.getAllSources();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void getSourceById_ShouldReturnSource_WhenFound() {
        // Given
        Source source = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null);
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
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null);
        when(sourceRepository.findByType("MANUAL")).thenReturn(Optional.of(manualSource));

        // When
        Optional<Source> result = sourceService.findByType("MANUAL");

        // Then
        assertTrue(result.isPresent());
        assertEquals("Saisie manuelle", result.get().name());
    }

    @Test
    void findOrCreateByName_ShouldReturnExistingSource_WhenAlreadyPresent() {
        // Given
        Source existing = new Source(2L, "OpenAgenda Marseille", "API", null, "ACTIVE", null);
        when(sourceRepository.findByName("OpenAgenda Marseille")).thenReturn(Optional.of(existing));

        // When
        Source result = sourceService.findOrCreateByName("OpenAgenda Marseille", "API", "https://api.openagenda.com");

        // Then
        assertEquals(2L, result.id());
        verify(sourceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void findOrCreateByName_ShouldCreateSource_WhenNotPresent() {
        // Given
        when(sourceRepository.findByName("Nouvelle Source")).thenReturn(Optional.empty());
        Source created = new Source(3L, "Nouvelle Source", "API", null, "ACTIVE", null);
        when(sourceRepository.save(any(Source.class))).thenReturn(created);

        // When
        Source result = sourceService.findOrCreateByName("Nouvelle Source", "API", null);

        // Then
        assertEquals(3L, result.id());
        verify(sourceRepository).save(any(Source.class));
    }

}
