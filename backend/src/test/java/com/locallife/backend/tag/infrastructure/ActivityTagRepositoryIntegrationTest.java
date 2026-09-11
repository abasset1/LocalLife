package com.locallife.backend.tag.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import com.locallife.backend.tag.domain.ActivityTag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11009), même patron que
 * les autres tests d'intégration du sprint. Couvre plusieurs tags pour
 * une même activité, l'unicité (activity_id, tag), et la contrainte
 * FOREIGN KEY.
 */
@SpringBootTest
@Transactional
class ActivityTagRepositoryIntegrationTest {

    @Autowired
    private ActivityTagRepository activityTagRepository;

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

        ActivityTag saved = activityTagRepository.save(new ActivityTag(null, activity.id(), "famille"));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findByActivityId_ShouldReturnMultipleTags_AndExcludeOthers() {
        // Critère d'acceptation « plusieurs tags par activité ».
        Activity activity = testActivity();
        Activity otherActivity = testActivity();
        ActivityTag familyTag = activityTagRepository.save(new ActivityTag(null, activity.id(), "famille"));
        ActivityTag freeTag = activityTagRepository.save(new ActivityTag(null, activity.id(), "gratuit"));
        ActivityTag otherTag = activityTagRepository.save(new ActivityTag(null, otherActivity.id(), "famille"));

        List<ActivityTag> result = activityTagRepository.findByActivityId(activity.id());

        assertThat(result).extracting(ActivityTag::id).contains(familyTag.id(), freeTag.id());
        assertThat(result).extracting(ActivityTag::id).doesNotContain(otherTag.id());
    }

    @Test
    void save_ShouldFail_WhenSameTagAttachedTwiceToSameActivity() {
        Activity activity = testActivity();
        activityTagRepository.save(new ActivityTag(null, activity.id(), "famille"));

        assertThatThrownBy(() -> activityTagRepository.save(new ActivityTag(null, activity.id(), "famille")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        assertThatThrownBy(() -> activityTagRepository.save(new ActivityTag(null, -1L, "famille")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByActivityId_ShouldReturnEmpty_WhenActivityHasNoTags() {
        Activity activity = testActivity();

        assertThat(activityTagRepository.findByActivityId(activity.id())).isEmpty();
    }
}
