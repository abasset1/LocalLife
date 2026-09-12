package com.locallife.backend.collector.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.collector.domain.ExternalActivity;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11010), même patron que
 * les autres tests d'intégration du sprint. {@code raw_payload}
 * (colonne {@code jsonb}) exerce à nouveau les convertisseurs de
 * LL-11009 (voir {@code ActivityMetadataRepositoryIntegrationTest}, déjà
 * le test le plus important pour ce mécanisme — celui-ci confirme sa
 * réutilisation sur une deuxième table).
 */
@SpringBootTest
@Transactional
class ExternalActivityRepositoryIntegrationTest {

    @Autowired
    private ExternalActivityRepository externalActivityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private Long testSourceId() {
        return sourceRepository.save(new Source(
                        null, "test-source-" + UUID.randomUUID(), "API", null, "ACTIVE", null, null, null))
                .id();
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Long sourceId = testSourceId();

        ExternalActivity saved = externalActivityRepository.save(new ExternalActivity(
                null, sourceId, "ext-123", "https://example.com/evenement/123", null,
                LocalDateTime.now(), "hash-1", "{\"title\": \"Marché de Noël\"}"));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findBySourceIdAndExternalId_ShouldReturnRawPayloadUnchanged_AfterRoundTripThroughJsonbColumn() {
        Long sourceId = testSourceId();
        String rawPayload = "{\"nested\": {\"array\": [1, 2, 3]}, \"text\": \"accents éàç\"}";

        externalActivityRepository.save(new ExternalActivity(
                null, sourceId, "ext-123", "https://example.com", null, LocalDateTime.now(), "hash-1", rawPayload));
        Optional<ExternalActivity> found = externalActivityRepository.findBySourceIdAndExternalId(
                sourceId, "ext-123");

        assertThat(found).isPresent();
        assertThat(found.get().rawPayload()).isEqualTo(rawPayload);
    }

    @Test
    void save_ShouldFail_WhenSameSourceAndExternalIdSavedTwiceWithoutId() {
        // Contrainte UNIQUE(source_id, external_id) : ExternalActivityService#upsert existe
        // précisément pour éviter ce cas en pratique (voir ExternalActivityServiceTest).
        Long sourceId = testSourceId();
        externalActivityRepository.save(new ExternalActivity(
                null, sourceId, "ext-123", "https://example.com", null, LocalDateTime.now(), "hash-1", "{}"));

        assertThatThrownBy(() -> externalActivityRepository.save(new ExternalActivity(
                null, sourceId, "ext-123", "https://example.com", null, LocalDateTime.now(), "hash-2", "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenSourceDoesNotExist() {
        assertThatThrownBy(() -> externalActivityRepository.save(new ExternalActivity(
                null, -1L, "ext-123", "https://example.com", null, LocalDateTime.now(), "hash-1", "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findBySourceIdAndExternalId_ShouldReturnEmpty_WhenNotYetCollected() {
        Long sourceId = testSourceId();

        assertThat(externalActivityRepository.findBySourceIdAndExternalId(sourceId, "inconnu")).isEmpty();
    }
}
