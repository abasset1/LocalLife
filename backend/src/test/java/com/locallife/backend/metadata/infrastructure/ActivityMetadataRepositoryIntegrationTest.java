package com.locallife.backend.metadata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.metadata.domain.ActivityMetadata;
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
 * Tests d'intégration contre la base réelle (LL-11009) : c'est ici,
 * contre une vraie colonne {@code jsonb}, que
 * {@link StringToJsonbConverter}/{@link JsonbToStringConverter} sont
 * réellement exercés de bout en bout (voir
 * {@code docs/02_Architecture/ADR-0004-metadata-jsonb.md}) — le point le
 * plus important à vérifier via {@code mvn verify} pour ce ticket.
 */
@SpringBootTest
@Transactional
class ActivityMetadataRepositoryIntegrationTest {

    @Autowired
    private ActivityMetadataRepository activityMetadataRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private Long manualSourceId() {
        return sourceRepository.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException("Source MANUAL introuvable — migration V8 manquante ?"));
    }

    private Activity testActivity() {
        return activityRepository.save(new Activity(
                null, "test-activity-" + UUID.randomUUID(), "description", "sport",
                43.9493, 4.8055, LocalDateTime.now(), null, "PUBLISHED", manualSourceId(), null, null,
                null, null, null, null, null, null, null));
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Activity activity = testActivity();

        ActivityMetadata saved = activityMetadataRepository.save(
                new ActivityMetadata(null, activity.id(), "{\"sourceField\": \"valeur\"}"));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findByActivityId_ShouldReturnDataUnchanged_AfterRoundTripThroughJsonbColumn() {
        // Le test le plus important de ce ticket : si les convertisseurs (ADR-0004) ou leur
        // enregistrement (JdbcConfig) sont mal configurés, c'est ici que ça échouera.
        Activity activity = testActivity();
        String json = "{\"nested\": {\"array\": [1, 2, 3], \"flag\": true}, \"text\": \"accents éàç\"}";

        activityMetadataRepository.save(new ActivityMetadata(null, activity.id(), json));
        Optional<ActivityMetadata> found = activityMetadataRepository.findByActivityId(activity.id());

        assertThat(found).isPresent();
        assertThat(found.get().data()).isEqualTo(json);
    }

    @Test
    void save_ShouldFail_WhenActivityAlreadyHasMetadata() {
        // Contrainte UNIQUE(activity_id) : un seul enregistrement metadata par activité.
        Activity activity = testActivity();
        activityMetadataRepository.save(new ActivityMetadata(null, activity.id(), "{}"));

        assertThatThrownBy(() -> activityMetadataRepository.save(new ActivityMetadata(null, activity.id(), "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        assertThatThrownBy(() -> activityMetadataRepository.save(new ActivityMetadata(null, -1L, "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByActivityId_ShouldReturnEmpty_WhenNoMetadataForActivity() {
        Activity activity = testActivity();

        assertThat(activityMetadataRepository.findByActivityId(activity.id())).isEmpty();
    }
}
