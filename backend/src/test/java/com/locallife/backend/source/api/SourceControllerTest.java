package com.locallife.backend.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SourceControllerTest {

    @Mock
    private SourceService sourceService;

    @InjectMocks
    private SourceController sourceController;

    @Test
    void getAllSources_ShouldReturnListOfSources() {
        // Given
        List<Source> sources = List.of(
                new Source(1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "ACTIVE", null),
                new Source(2L, "Contribution manuelle", "MANUAL", null, "ACTIVE", null)
        );
        when(sourceService.getAllSources()).thenReturn(sources);

        // When
        ResponseEntity<List<Source>> response = sourceController.getAllSources();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void getAllSources_ShouldReturnEmptyList_WhenNoSources() {
        // Given
        when(sourceService.getAllSources()).thenReturn(List.of());

        // When
        ResponseEntity<List<Source>> response = sourceController.getAllSources();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void getSourceById_ShouldReturnSource_WhenSourceExists() {
        // Given
        Source source = new Source(1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "ACTIVE", null);
        when(sourceService.getSourceById(1L)).thenReturn(Optional.of(source));

        // When
        ResponseEntity<Source> response = sourceController.getSourceById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OpenAgenda Marseille", response.getBody().name());
    }

    @Test
    void getSourceById_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        // Given
        when(sourceService.getSourceById(99L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Source> response = sourceController.getSourceById(99L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

}
