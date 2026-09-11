package com.locallife.backend.accessibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
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
 * Tests d'intégration contre la base réelle (LL-11009), même patron que
 * les autres tests d'intégration du sprint. Couvre la persistance de
 * base (dont la distinction {@code null}/{@code false}, voir la javadoc
 * de {@code Accessibility}), l'unicité {@code activity_id}, et la
 * contrainte FOREIGN KEY.
 */
@SpringBootTest
@Transactional
class AccessibilityRepositoryIntegrationTest {

    @Autowired
    private AccessibilityRepository accessibilityRepository;

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

        Accessibility saved = accessibilityRepository.save(
                new Accessibility(null, activity.id(), true, false, null, null, null));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findByActivityId_ShouldReturnSavedAccessibility_DistinguishingNullFromFalse() {
        Activity activity = testActivity();
        Accessibility accessibility = new Accessibility(null, activity.id(), true, false, null, null, null);

        accessibilityRepository.save(accessibility);
        Optional<Accessibility> found = accessibilityRepository.findByActivityId(activity.id());

        assertThat(found).isPresent();
        assertThat(found.get().motorImpairment()).isTrue();
        assertThat(found.get().hearingImpairment()).isFalse();
        assertThat(found.get().visualImpairment()).isNull();
    }

    @Test
    void save_ShouldFail_WhenActivityAlreadyHasAccessibility() {
        // Contrainte UNIQUE(activity_id) : une seule fiche d'accessibilité par activité.
        Activity activity = testActivity();
        accessibilityRepository.save(new Accessibility(null, activity.id(), true, null, null, null, null));

        assertThatThrownBy(() -> accessibilityRepository.save(
                new Accessibility(null, activity.id(), false, null, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        assertThatThrownBy(() -> accessibilityRepository.save(
                new Accessibility(null, -1L, true, null, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByActivityId_ShouldReturnEmpty_WhenNoAccessibilityForActivity() {
        Activity activity = testActivity();

        assertThat(accessibilityRepository.findByActivityId(activity.id())).isEmpty();
    }
}
