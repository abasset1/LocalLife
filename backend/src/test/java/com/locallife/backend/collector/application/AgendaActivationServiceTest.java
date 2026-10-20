package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository.CatalogEntry;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendaActivationServiceTest {

    @Mock
    private AgendaDiscoveryRepository repository;

    @Mock
    private SourceService sourceService;

    private AgendaActivationService service(int maxBatch) {
        return new AgendaActivationService(repository, sourceService, maxBatch);
    }

    private static CatalogEntry entry(String uid, String title, String slug) {
        return new CatalogEntry(uid, title, slug, true, null, null, null, null, null);
    }

    @Test
    void createSources_ShouldCreateACollectibleSource_ForEachSelectedAgendaWithoutSource() {
        when(repository.findByUids(Set.of("62695", "125325"))).thenReturn(List.of(
                entry("62695", "  Patrimoine Grand Châtellerault ", "patrimoine"), entry("125325", null, null)));
        when(sourceService.getAllSources()).thenReturn(List.of());
        when(sourceService.createSource(any(), any(), any(), any(), any())).thenAnswer(invocation -> new Source(
                7L, invocation.getArgument(0), "API", invocation.getArgument(2), "ACTIVE", null,
                invocation.getArgument(3), null));

        AgendaActivationResult result = service(50).createSources(List.of("62695", "125325", "62695"));

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.created()).hasSize(2);
        verify(sourceService).createSource(
                "Patrimoine Grand Châtellerault", "API", "https://openagenda.com/patrimoine", "62695", null);
        verify(sourceService).createSource("Agenda OpenAgenda 125325", "API", null, "125325", null);
    }

    @Test
    void createSources_ShouldLeaveKnownAgendasUntouched_EvenWhenTheirSourceIsInactive() {
        when(repository.findByUids(Set.of("62695"))).thenReturn(List.of(entry("62695", "Patrimoine", "p")));
        when(sourceService.getAllSources()).thenReturn(List.of(
                new Source(3L, "Patrimoine", "API", null, "INACTIVE", null, "62695", null)));

        AgendaActivationResult result = service(50).createSources(List.of("62695"));

        assertThat(result.alreadyKnown()).containsExactly("62695");
        assertThat(result.created()).isEmpty();
        verify(sourceService, never()).createSource(any(), any(), any(), any(), any());
    }

    @Test
    void createSources_ShouldReportUidsAbsentFromTheCatalog_WithoutCreatingThem() {
        when(repository.findByUids(Set.of("999"))).thenReturn(List.of());
        when(sourceService.getAllSources()).thenReturn(List.of());

        AgendaActivationResult result = service(50).createSources(List.of("999"));

        assertThat(result.notFound()).containsExactly("999");
        verify(sourceService, never()).createSource(any(), any(), any(), any(), any());
    }

    @Test
    void createSources_ShouldRefuseAnOversizedOrEmptySelection_Entirely() {
        assertThatThrownBy(() -> service(2).createSources(List.of("1", "2", "3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au plus 2");
        assertThatThrownBy(() -> service(2).createSources(List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(2).createSources(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, sourceService);
    }
}
