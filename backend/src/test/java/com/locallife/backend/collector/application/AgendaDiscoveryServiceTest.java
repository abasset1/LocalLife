package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Règles d'entrée de la découverte ; le parcours lui-même (pages, reprise, statuts) est vérifié sur la vraie
 * base par {@code AgendaDiscoveryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AgendaDiscoveryServiceTest {

    @Mock
    private OpenAgendaAgendaDirectory directory;

    @Mock
    private AgendaDiscoveryRepository repository;

    private AgendaDiscoveryService service() {
        return new AgendaDiscoveryService(directory, repository, TransactionOperations.withoutTransaction(),
                Clock.systemUTC(), 20);
    }

    @Test
    void discover_ShouldRefuseABatchOutsideItsExplicitBounds_BeforeAnyCall() {
        assertThatThrownBy(() -> service().discover(DiscoveryScope.ALL, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().discover(DiscoveryScope.ALL,
                AgendaDiscoveryService.MAX_PAGES_PER_BATCH + 1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(AgendaDiscoveryService.MAX_PAGES_PER_BATCH));
        verifyNoInteractions(directory, repository);
    }

    @Test
    void discover_ShouldNotCreateARun_WhenTheApiKeyIsMissing() {
        doThrow(new CollectorException("OPENAGENDA_API_KEY doit être configurée.", null))
                .when(directory).requireApiKey();

        assertThatThrownBy(() -> service().discover(DiscoveryScope.ALL, null, false))
                .isInstanceOf(CollectorException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void constructor_ShouldRefuseAnInvalidConfiguredBatch() {
        assertThatThrownBy(() -> new AgendaDiscoveryService(directory, repository,
                TransactionOperations.withoutTransaction(), Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursor_ShouldRoundTripThroughItsStoredForm_NullValuesIncluded() {
        List<Object> cursor = Arrays.asList(1700000000000L, "abc", null);

        String stored = AgendaDiscoveryService.serializeCursor(cursor);

        assertThat(stored).isEqualTo("[1700000000000,\"abc\",null]");
        assertThat(AgendaDiscoveryService.parseCursor(stored)).isEqualTo(cursor);
    }

    @Test
    void cursor_ShouldBeNull_WhenTheProviderSignalsTheEnd() {
        assertThat(AgendaDiscoveryService.serializeCursor(null)).isNull();
        assertThat(AgendaDiscoveryService.serializeCursor(List.of())).isNull();
        assertThat(AgendaDiscoveryService.parseCursor(null)).isNull();
    }

    @Test
    void isAbnormallyLong_ShouldFlagACursorCycleSpanningSeveralBatches() {
        assertThat(AgendaDiscoveryService.isAbnormallyLong(300, 100)).isFalse();
        assertThat(AgendaDiscoveryService.isAbnormallyLong(301, 100)).isTrue();
        assertThat(AgendaDiscoveryService.isAbnormallyLong(1_000_000, null)).isFalse();
    }
}
