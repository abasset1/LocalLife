package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.domain.ExternalActivity;
import com.locallife.backend.collector.infrastructure.ExternalActivityRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre {@link ExternalActivityService#computeHash} (déterminisme,
 * sensibilité au contenu) et {@link ExternalActivityService#upsert}
 * (création vs mise à jour, critère d'acceptation « détection de
 * modification possible ») — LL-11010.
 */
@ExtendWith(MockitoExtension.class)
class ExternalActivityServiceTest {

    @Mock
    private ExternalActivityRepository externalActivityRepository;

    private ExternalActivityService externalActivityService;

    @BeforeEach
    void setUp() {
        externalActivityService = new ExternalActivityService(externalActivityRepository);
    }

    @Test
    void computeHash_ShouldBeDeterministic_ForSamePayload() {
        String payload = "{\"title\":\"Marché de Noël\"}";

        assertThat(externalActivityService.computeHash(payload))
                .isEqualTo(externalActivityService.computeHash(payload));
    }

    @Test
    void computeHash_ShouldDiffer_ForDifferentPayloads() {
        String first = externalActivityService.computeHash("{\"title\":\"Marché de Noël\"}");
        String second = externalActivityService.computeHash("{\"title\":\"Marché de Noël modifié\"}");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void upsert_ShouldCreate_WhenNoExistingRecordForSourceAndExternalId() {
        ExternalActivity toUpsert = new ExternalActivity(
                null, 1L, "ext-123", "https://example.com/evenement/123", null, null,
                "hash-ignoré-recalculé", "{\"title\":\"Marché de Noël\"}");
        when(externalActivityRepository.findBySourceIdAndExternalId(1L, "ext-123")).thenReturn(Optional.empty());
        when(externalActivityRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ExternalActivity result = externalActivityService.upsert(toUpsert);

        assertThat(result.id()).isNull();
        assertThat(result.payloadHash()).isEqualTo(externalActivityService.computeHash(toUpsert.rawPayload()));
        assertThat(result.lastSeenAt()).isNotNull();
    }

    @Test
    void upsert_ShouldUpdateExistingRecord_WhenSourceAndExternalIdAlreadyKnown() {
        // « détection de modification possible » : payloadHash change bien entre l'existant
        // (payload différent) et le nouveau, tout en conservant le même id.
        ExternalActivity existing = new ExternalActivity(
                5L, 1L, "ext-123", "https://example.com/evenement/123", null,
                LocalDateTime.of(2026, 1, 1, 0, 0), "ancien-hash", "{\"title\":\"Ancien titre\"}");
        ExternalActivity recollected = new ExternalActivity(
                null, 1L, "ext-123", "https://example.com/evenement/123", null, null,
                "ignoré", "{\"title\":\"Nouveau titre\"}");
        when(externalActivityRepository.findBySourceIdAndExternalId(1L, "ext-123"))
                .thenReturn(Optional.of(existing));
        when(externalActivityRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ExternalActivity result = externalActivityService.upsert(recollected);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.payloadHash()).isNotEqualTo(existing.payloadHash());
        assertThat(result.rawPayload()).isEqualTo("{\"title\":\"Nouveau titre\"}");
    }

    @Test
    void upsert_ShouldPersistRecalculatedFields_RegardlessOfCallerSuppliedValues() {
        ExternalActivity toUpsert = new ExternalActivity(
                null, 1L, "ext-123", "https://example.com", null,
                LocalDateTime.of(2020, 1, 1, 0, 0), "faux-hash-ignoré", "{}");
        when(externalActivityRepository.findBySourceIdAndExternalId(1L, "ext-123")).thenReturn(Optional.empty());
        ArgumentCaptor<ExternalActivity> captor = ArgumentCaptor.forClass(ExternalActivity.class);
        when(externalActivityRepository.save(captor.capture())).thenAnswer(call -> call.getArgument(0));

        externalActivityService.upsert(toUpsert);

        ExternalActivity saved = captor.getValue();
        assertThat(saved.payloadHash()).isEqualTo(externalActivityService.computeHash("{}"));
        assertThat(saved.lastSeenAt()).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
    }
}
