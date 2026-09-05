package com.locallife.backend.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.source.api.SourceController.CreateSourceRequest;
import com.locallife.backend.source.api.SourceController.UpdateSourceRequest;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import jakarta.servlet.http.HttpServletRequest;
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

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private SourceController sourceController;

    @Test
    void getAllSources_ShouldReturnListOfSources() {
        // Given
        List<Source> sources = List.of(
                new Source(1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "ACTIVE", null, null, null),
                new Source(2L, "Contribution manuelle", "MANUAL", null, "ACTIVE", null, null, null)
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
        Source source = new Source(1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "ACTIVE", null,
                null, null);
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

    @Test
    void createSource_ShouldReturnCreated_WhenRequestIsValid() {
        // Given
        CreateSourceRequest request = new CreateSourceRequest(
                "OpenAgenda Marseille", "API", "https://openagenda.com", "agenda-uid", "PACA");
        Source created = new Source(
                1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "ACTIVE", null, "agenda-uid", "PACA");
        when(sourceService.createSource("OpenAgenda Marseille", "API", "https://openagenda.com", "agenda-uid", "PACA"))
                .thenReturn(created);

        // When
        ResponseEntity<Object> response = sourceController.createSource(request, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(created, response.getBody());
    }

    @Test
    void createSource_ShouldReturnBadRequest_WhenNameIsBlank() {
        // Given
        CreateSourceRequest request = new CreateSourceRequest("", "API", null, null, null);
        when(sourceService.createSource("", "API", null, null, null))
                .thenThrow(new IllegalArgumentException("Le nom de la source est obligatoire."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/sources");

        // When
        ResponseEntity<Object> response = sourceController.createSource(request, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Le nom de la source est obligatoire.", ((ErrorResponse) response.getBody()).message());
    }

    @Test
    void updateSource_ShouldReturnOk_WhenSourceExists() {
        // Given
        UpdateSourceRequest request = new UpdateSourceRequest(
                "OpenAgenda Marseille", "API", "https://openagenda.com", "INACTIVE", "agenda-uid", "PACA");
        Source updated = new Source(
                1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "INACTIVE", null, "agenda-uid", "PACA");
        when(sourceService.updateSource(1L, "OpenAgenda Marseille", "API", "https://openagenda.com", "INACTIVE",
                "agenda-uid", "PACA")).thenReturn(Optional.of(updated));

        // When
        ResponseEntity<Object> response = sourceController.updateSource(1L, request, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updated, response.getBody());
    }

    @Test
    void updateSource_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        // Given
        UpdateSourceRequest request = new UpdateSourceRequest("Nom", "API", null, "ACTIVE", null, null);
        when(sourceService.updateSource(99L, "Nom", "API", null, "ACTIVE", null, null))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<Object> response = sourceController.updateSource(99L, request, httpRequest);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updateSource_ShouldReturnBadRequest_WhenTypeIsBlank() {
        // Given
        UpdateSourceRequest request = new UpdateSourceRequest("Nom", "", null, "ACTIVE", null, null);
        when(sourceService.updateSource(1L, "Nom", "", null, "ACTIVE", null, null))
                .thenThrow(new IllegalArgumentException("Le type de la source est obligatoire."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/sources/1");

        // When
        ResponseEntity<Object> response = sourceController.updateSource(1L, request, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Le type de la source est obligatoire.", ((ErrorResponse) response.getBody()).message());
    }

    @Test
    void deleteSource_ShouldReturnNoContent_WhenSourceExists() {
        // Given
        Source source = new Source(1L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, null, null);
        when(sourceService.deleteSource(1L)).thenReturn(Optional.of(source));

        // When
        ResponseEntity<Object> response = sourceController.deleteSource(1L, httpRequest);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void deleteSource_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        // Given
        when(sourceService.deleteSource(99L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Object> response = sourceController.deleteSource(99L, httpRequest);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteSource_ShouldReturnBadRequest_WhenSourceIsReservedManual() {
        // Given
        when(sourceService.deleteSource(1L))
                .thenThrow(new IllegalArgumentException("La source réservée 'MANUAL' ne peut pas être supprimée."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/sources/1");

        // When
        ResponseEntity<Object> response = sourceController.deleteSource(1L, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La source réservée 'MANUAL' ne peut pas être supprimée.",
                ((ErrorResponse) response.getBody()).message());
    }

}
