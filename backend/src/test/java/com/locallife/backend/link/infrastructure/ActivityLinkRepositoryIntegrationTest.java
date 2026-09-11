package com.locallife.backend.link.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11008), même patron que
 * {@code ActivityMediaRepositoryIntegrationTest}. Couvre plusieurs
 * liens pour une même activité, un lien sans libellé, et la contrainte
 * FOREIGN KEY (V23__create_activity_link_table.sql).
 */
@SpringBootTest
@Transactional
class ActivityLinkRepositoryIntegrationTest {

    @Autowired
    private ActivityLinkRepository activityLinkRepository;

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

        ActivityLink saved = activityLinkRepository.save(
                new ActivityLink(null, activity.id(), "WEBSITE", "Site officiel", "https://example.com"));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findById_ShouldReturnSavedLink_WithAllFieldsIntact() {
        Activity activity = testActivity();
        ActivityLink link = new ActivityLink(null, activity.id(), "WEBSITE", "Site officiel", "https://example.com");

        ActivityLink saved = activityLinkRepository.save(link);
        Optional<ActivityLink> found = activityLinkRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().activityId()).isEqualTo(activity.id());
        assertThat(found.get().type()).isEqualTo("WEBSITE");
        assertThat(found.get().label()).isEqualTo("Site officiel");
        assertThat(found.get().value()).isEqualTo("https://example.com");
    }

    @Test
    void save_ShouldSucceed_WhenLabelIsAbsent() {
        Activity activity = testActivity();

        ActivityLink saved = activityLinkRepository.save(
                new ActivityLink(null, activity.id(), "WEBSITE", null, "https://example.com"));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.label()).isNull();
    }

    @Test
    void findByActivityId_ShouldReturnMultipleLinks_AndExcludeOthers() {
        // Critère d'acceptation « plusieurs liens possibles ».
        Activity activity = testActivity();
        Activity otherActivity = testActivity();
        ActivityLink website = activityLinkRepository.save(
                new ActivityLink(null, activity.id(), "WEBSITE", "Site", "https://example.com"));
        ActivityLink email = activityLinkRepository.save(
                new ActivityLink(null, activity.id(), "EMAIL", "Contact", "contact@example.com"));
        ActivityLink other = activityLinkRepository.save(
                new ActivityLink(null, otherActivity.id(), "WEBSITE", "Site", "https://example.org"));

        List<ActivityLink> result = activityLinkRepository.findByActivityId(activity.id());

        assertThat(result).extracting(ActivityLink::id).contains(website.id(), email.id());
        assertThat(result).extracting(ActivityLink::id).doesNotContain(other.id());
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        ActivityLink orphan = new ActivityLink(null, -1L, "WEBSITE", "Site", "https://example.com");

        assertThatThrownBy(() -> activityLinkRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_ShouldReturnEmpty_WhenLinkDoesNotExist() {
        assertThat(activityLinkRepository.findById(-1L)).isEmpty();
    }
}
