package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendaDiscoveryServiceTest {

    @Mock
    private OpenAgendaAgendaDirectory agendaDirectory;

    @Mock
    private SourceService sourceService;

    @InjectMocks
    private AgendaDiscoveryService agendaDiscoveryService;

    @Test
    void discoverOfficialAgendas_ShouldCreateACollectibleSource_ForEachUnknownAgenda() {
        when(agendaDirectory.listOfficialAgendas(50)).thenReturn(List.of(
                new DiscoveredAgenda("62695", " Patrimoine Grand Châtellerault ", "patrimoine-grand-chatellerault"),
                new DiscoveredAgenda("125325", null, null)));
        when(sourceService.getAllSources()).thenReturn(List.of(
                new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null)));

        AgendaDiscoveryResult result = agendaDiscoveryService.discoverOfficialAgendas(50);

        assertEquals(2, result.listed());
        assertEquals(2, result.created());
        assertEquals(0, result.alreadyKnown());
        assertEquals(List.of("Patrimoine Grand Châtellerault", "Agenda OpenAgenda 125325"), result.createdNames());
        verify(sourceService).createSource(
                "Patrimoine Grand Châtellerault", "API", "https://openagenda.com/patrimoine-grand-chatellerault",
                "62695", null);
        // Sans titre ni slug : nom de repli, pas d'URL.
        verify(sourceService).createSource("Agenda OpenAgenda 125325", "API", null, "125325", null);
    }

    @Test
    void discoverOfficialAgendas_ShouldLeaveKnownAgendasUntouched_EvenWhenInactive() {
        // Une source désactivée par un administrateur ne doit pas être recréée par la découverte.
        when(agendaDirectory.listOfficialAgendas(50)).thenReturn(List.of(
                new DiscoveredAgenda("62695", "Patrimoine", "patrimoine")));
        when(sourceService.getAllSources()).thenReturn(List.of(
                new Source(7L, "Patrimoine (désactivé)", "API", null, "INACTIVE", null, "62695", null)));

        AgendaDiscoveryResult result = agendaDiscoveryService.discoverOfficialAgendas(50);

        assertEquals(0, result.created());
        assertEquals(1, result.alreadyKnown());
        verify(sourceService, never()).createSource(any(), any(), any(), any(), any());
    }

    @Test
    void discoverOfficialAgendas_ShouldCreateOnlyOneSource_WhenTheSameAgendaIsListedTwice() {
        when(agendaDirectory.listOfficialAgendas(50)).thenReturn(List.of(
                new DiscoveredAgenda("62695", "Patrimoine", "patrimoine"),
                new DiscoveredAgenda("62695", "Patrimoine", "patrimoine")));
        when(sourceService.getAllSources()).thenReturn(List.of());

        AgendaDiscoveryResult result = agendaDiscoveryService.discoverOfficialAgendas(50);

        assertEquals(1, result.created());
        assertEquals(1, result.alreadyKnown());
    }

}
