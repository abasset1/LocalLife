package com.locallife.backend.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11007), même patron que
 * {@code ScheduleRepositoryIntegrationTest}. Couvre plusieurs médias
 * pour une même activité (critère « plusieurs images possibles »),
 * l'ordre de lecture (« ordre conservé »), l'unicité (activity_id,
 * media_id), et les contraintes FOREIGN KEY (V22__create_media_tables.sql).
 */
@SpringBootTest
@Transactional
class ActivityMediaRepositoryIntegrationTest {

    @Autowired
    private ActivityMediaRepository activityMediaRepository;

    @Autowired
    private MediaRepository mediaRepository;

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

    private Media testMedia() {
        return mediaRepository.save(
                new Media(null, "https://example.com/photo.jpg", "IMAGE", 1200, 800, "Ville d'Avignon", null));
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Activity activity = testActivity();
        Media media = testMedia();

        ActivityMedia saved = activityMediaRepository.save(new ActivityMedia(null, activity.id(), media.id(), 0));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findByActivityIdOrderByPosition_ShouldReturnMultipleMedia_InPositionOrder() {
        // Critères d'acceptation « plusieurs images possibles » + « ordre conservé ». Les médias
        // sont insérés dans le désordre pour vérifier que le tri vient bien de la requête, pas de
        // l'ordre d'insertion.
        Activity activity = testActivity();
        Media first = testMedia();
        Media second = testMedia();
        Media third = testMedia();
        activityMediaRepository.save(new ActivityMedia(null, activity.id(), second.id(), 1));
        activityMediaRepository.save(new ActivityMedia(null, activity.id(), third.id(), 2));
        activityMediaRepository.save(new ActivityMedia(null, activity.id(), first.id(), 0));

        List<ActivityMedia> result = activityMediaRepository.findByActivityIdOrderByPosition(activity.id());

        assertThat(result).extracting(ActivityMedia::mediaId)
                .containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void save_ShouldFail_WhenSameMediaAttachedTwiceToSameActivity() {
        // Contrainte UNIQUE (activity_id, media_id) de V22__create_media_tables.sql.
        Activity activity = testActivity();
        Media media = testMedia();
        activityMediaRepository.save(new ActivityMedia(null, activity.id(), media.id(), 0));

        assertThatThrownBy(() ->
                activityMediaRepository.save(new ActivityMedia(null, activity.id(), media.id(), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenActivityDoesNotExist() {
        Media media = testMedia();
        ActivityMedia orphan = new ActivityMedia(null, -1L, media.id(), 0);

        assertThatThrownBy(() -> activityMediaRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_ShouldFail_WhenMediaDoesNotExist() {
        Activity activity = testActivity();
        ActivityMedia orphan = new ActivityMedia(null, activity.id(), -1L, 0);

        assertThatThrownBy(() -> activityMediaRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByActivityIdOrderByPosition_ShouldReturnEmpty_WhenActivityHasNoMedia() {
        Activity activity = testActivity();

        assertThat(activityMediaRepository.findByActivityIdOrderByPosition(activity.id())).isEmpty();
    }
}
