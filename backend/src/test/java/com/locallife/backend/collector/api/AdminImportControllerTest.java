package com.locallife.backend.collector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminImportControllerTest {

    @Mock
    private ImportService importService;

    @InjectMocks
    private AdminImportController adminImportController;

    @Test
    void triggerImport_ShouldReturnOkWithResults_WhenImportSucceeds() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        ImportResult result = new ImportResult("OpenAgenda", now, now, 3, 2, 1, 0, 0, 0);
        when(importService.importAll()).thenReturn(List.of(result));

        // When
        ResponseEntity<List<ImportResult>> response = adminImportController.triggerImport();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(result);
        verify(importService).importAll();
    }

    @Test
    void triggerImport_ShouldReturnOkWithDegradedResult_WhenCollectorFails() {
        // Given : ImportService capture déjà les échecs de collecte (voir sa javadoc) — un résultat
        // dégradé (errors=1, fetched=0) reste un 200, pas une erreur HTTP (voir AdminImportController).
        LocalDateTime now = LocalDateTime.now();
        ImportResult degraded = new ImportResult("OpenAgenda", now, now, 0, 0, 0, 0, 1, 0);
        when(importService.importAll()).thenReturn(List.of(degraded));

        // When
        ResponseEntity<List<ImportResult>> response = adminImportController.triggerImport();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(degraded);
    }

    @Test
    void triggerImport_ShouldReturnOkWithEmptyList_WhenNoCollectorRegistered() {
        // Given
        when(importService.importAll()).thenReturn(List.of());

        // When
        ResponseEntity<List<ImportResult>> response = adminImportController.triggerImport();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

}
