package com.locallife.backend.collector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.application.AgendaActivationService;
import com.locallife.backend.collector.application.AgendaDiscoveryResult;
import com.locallife.backend.collector.application.AgendaDiscoveryRun;
import com.locallife.backend.collector.application.AgendaDiscoveryService;
import com.locallife.backend.collector.application.DiscoveredAgendaCatalogService;
import com.locallife.backend.collector.application.DiscoveryAlreadyRunningException;
import com.locallife.backend.collector.application.ImportAlreadyRunningException;
import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import com.locallife.backend.collector.application.SourceActivationService;
import com.locallife.backend.collector.application.SourceDryRunReport;
import com.locallife.backend.collector.application.SourceDryRunService;
import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.SourceSyncStateRepository;
import com.locallife.backend.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
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
class AdminImportControllerTest {

    @Mock
    private ImportService importService;

    @Mock
    private AgendaDiscoveryService agendaDiscoveryService;

    @Mock
    private SourceDryRunService sourceDryRunService;

    @Mock
    private SourceActivationService sourceActivationService;

    @Mock
    private AgendaActivationService agendaActivationService;

    @Mock
    private DiscoveredAgendaCatalogService catalogService;

    @Mock
    private SourceSyncStateRepository sourceSyncStateRepository;

    @InjectMocks
    private AdminImportController adminImportController;

    private static SourceDryRunReport report(SourceDryRunReport.Verdict verdict, boolean activated) {
        return new SourceDryRunReport(20L, "DATAtourisme PACA", verdict, List.of(), 3, 0, 3, 0, 0, 0,
                List.of("Concert"), activated);
    }

    @Test
    void dryRun_ShouldReturnReport_OrNotFound() {
        SourceDryRunReport suspicious = report(SourceDryRunReport.Verdict.SUSPICIOUS, false);
        when(sourceDryRunService.dryRun(20L)).thenReturn(Optional.of(suspicious));
        when(sourceDryRunService.dryRun(99L)).thenReturn(Optional.empty());

        assertThat(adminImportController.dryRun(20L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminImportController.dryRun(20L).getBody()).isEqualTo(suspicious);
        assertThat(adminImportController.dryRun(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void activate_ShouldReturnOk_WhenActivated_AndConflict_WhenRefused() {
        when(sourceActivationService.activate(20L))
                .thenReturn(Optional.of(report(SourceDryRunReport.Verdict.OK, true)));
        when(sourceActivationService.activate(21L))
                .thenReturn(Optional.of(report(SourceDryRunReport.Verdict.FAILED, false)));
        when(sourceActivationService.activate(99L)).thenReturn(Optional.empty());

        assertThat(adminImportController.activate(20L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminImportController.activate(21L).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(adminImportController.activate(21L).getBody().activated()).isFalse();
        assertThat(adminImportController.activate(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triggerImport_ShouldReturnOkWithResults_WhenImportSucceeds() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        ImportResult result = new ImportResult("OpenAgenda", now, now, 3, 2, 1, 0, 0, 0, 0);
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
        ImportResult degraded = new ImportResult("OpenAgenda", now, now, 0, 0, 0, 0, 1, 0, 0);
        when(importService.importAll()).thenReturn(List.of(degraded));

        // When
        ResponseEntity<List<ImportResult>> response = adminImportController.triggerImport();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(degraded);
    }

    private static AgendaDiscoveryResult discoveryResult(AgendaDiscoveryRun.Status status) {
        return new AgendaDiscoveryResult(1L, DiscoveryScope.ALL, status,
                status == AgendaDiscoveryRun.Status.COMPLETE, status == AgendaDiscoveryRun.Status.IN_PROGRESS,
                "raison", 2, 2, 200, 150, 50, 0, 0, null, null, null, null);
    }

    @Test
    void discoverAgendas_ShouldDefaultToTheWholeDirectory_AndReturnTheBatchReport() {
        AgendaDiscoveryResult result = discoveryResult(AgendaDiscoveryRun.Status.IN_PROGRESS);
        when(agendaDiscoveryService.discover(DiscoveryScope.ALL, null, false)).thenReturn(result);

        ResponseEntity<Object> response = adminImportController.discoverAgendas(
                null, null, false, org.mockito.Mockito.mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(result);
    }

    @Test
    void discoverAgendas_ShouldPassScopeBatchAndRestart() {
        AgendaDiscoveryResult result = discoveryResult(AgendaDiscoveryRun.Status.COMPLETE);
        when(agendaDiscoveryService.discover(DiscoveryScope.OFFICIAL, 5, true)).thenReturn(result);

        ResponseEntity<Object> response = adminImportController.discoverAgendas(
                "official", 5, true, org.mockito.Mockito.mock(HttpServletRequest.class));

        assertThat(response.getBody()).isEqualTo(result);
        verify(agendaDiscoveryService).discover(DiscoveryScope.OFFICIAL, 5, true);
    }

    @Test
    void discoverAgendas_ShouldReturnBadRequest_WhenScopeOrBatchIsInvalid() {
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(agendaDiscoveryService.discover(DiscoveryScope.ALL, 0, false))
                .thenThrow(new IllegalArgumentException("maxPages"));

        assertThat(adminImportController.discoverAgendas("PRIVE", null, false, httpRequest).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(adminImportController.discoverAgendas(null, 0, false, httpRequest).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void discoverAgendas_ShouldReturnBadGateway_WhenTheApiKeyIsMissing() {
        when(agendaDiscoveryService.discover(DiscoveryScope.ALL, null, false))
                .thenThrow(new CollectorException("OPENAGENDA_API_KEY doit être configurée.", null));

        ResponseEntity<Object> response = adminImportController.discoverAgendas(
                null, null, false, org.mockito.Mockito.mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void createSourcesFromCatalog_ShouldReturnBadRequest_ForAnOversizedSelection() {
        when(agendaActivationService.createSources(List.of("1", "2")))
                .thenThrow(new IllegalArgumentException("au plus 1 par lot"));

        ResponseEntity<Object> response = adminImportController.createSourcesFromCatalog(
                new AdminImportController.AgendaSelectionRequest(List.of("1", "2")),
                org.mockito.Mockito.mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDiscoveryAlreadyRunning_ShouldReturnConflict() {
        ResponseEntity<ErrorResponse> response = adminImportController.handleDiscoveryAlreadyRunning(
                new DiscoveryAlreadyRunningException(), org.mockito.Mockito.mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleImportAlreadyRunning_ShouldReturnConflict() {
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/admin/import");

        ResponseEntity<ErrorResponse> response = adminImportController.handleImportAlreadyRunning(
                new ImportAlreadyRunningException(), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Un import est déjà en cours.");
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
