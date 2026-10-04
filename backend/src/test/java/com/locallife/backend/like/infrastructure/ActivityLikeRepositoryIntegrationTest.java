package com.locallife.backend.like.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.like.domain.ActivityLike;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LS-002), même patron que
 * {@code ActivityRepositoryIntegrationTest} : chaque test est englobé
 * dans une transaction annulée à la fin. Couvre en particulier ce qu'un
 * test unitaire (mocks) ne peut pas vérifier : la contrainte
 * {@code UNIQUE (user_id, activity_id)} réellement appliquée par
 * PostgreSQL, et le filtre {@code status = 'PUBLISHED'} de la requête SQL
 * {@code ActivityRepository#findLikedByUserId}.
 */
@SpringBootTest
@Transactional
class ActivityLikeRepositoryIntegrationTest {

    @Autowired
    private ActivityLikeRepository activityLikeRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserRepository userRepository;

    private Long manualSourceId() {
        return sourceRepository.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException("Source MANUAL introuvable en base de test."));
    }

    private User newUser() {
        return userRepository.save(new User(
                null, "user-" + UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Role.USER,
                LocalDateTime.now(), true));
    }

    private Activity newActivity(String status) {
        return activityRepository.save(new Activity(
                null, "Activité de test " + UUID.randomUUID(), "description", "loisir",
                43.2951, 5.3739, LocalDateTime.now(), null, status, manualSourceId(), null, null,
                null, null, null, null, null, null, null, null,
                1L));
    }

    @Test
    void save_ShouldPersistLike_AndExistsByUserIdAndActivityId_ShouldReturnTrue() {
        // Given
        User user = newUser();
        Activity activity = newActivity("PUBLISHED");

        // When
        activityLikeRepository.save(new ActivityLike(null, user.id(), activity.id(), LocalDateTime.now()));

        // Then
        assertThat(activityLikeRepository.existsByUserIdAndActivityId(user.id(), activity.id())).isTrue();
    }

    @Test
    void existsByUserIdAndActivityId_ShouldReturnFalse_WhenNoLikeExists() {
        User user = newUser();
        Activity activity = newActivity("PUBLISHED");

        assertThat(activityLikeRepository.existsByUserIdAndActivityId(user.id(), activity.id())).isFalse();
    }

    @Test
    void save_ShouldThrow_WhenSameUserLikesSameActivityTwice() {
        // Given : la contrainte UNIQUE (user_id, activity_id) elle-même (voir
        // V29__create_activity_likes_table.sql), pas seulement la vérification
        // applicative dans LikeService — cette dernière ne protège pas contre une
        // course entre deux requêtes concurrentes, la contrainte SQL si.
        User user = newUser();
        Activity activity = newActivity("PUBLISHED");
        activityLikeRepository.save(new ActivityLike(null, user.id(), activity.id(), LocalDateTime.now()));

        // When / Then
        assertThatThrownBy(() ->
                activityLikeRepository.save(new ActivityLike(null, user.id(), activity.id(), LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByUserIdAndActivityId_ShouldRemoveLike() {
        // Given
        User user = newUser();
        Activity activity = newActivity("PUBLISHED");
        activityLikeRepository.save(new ActivityLike(null, user.id(), activity.id(), LocalDateTime.now()));

        // When
        activityLikeRepository.deleteByUserIdAndActivityId(user.id(), activity.id());

        // Then
        assertThat(activityLikeRepository.existsByUserIdAndActivityId(user.id(), activity.id())).isFalse();
    }

    @Test
    void deleteByUserIdAndActivityId_ShouldBeNoOp_WhenNoLikeExists() {
        // Given : aucun like — voir LikeService#unlike pour le raisonnement sur
        // l'idempotence attendue ici.
        User user = newUser();
        Activity activity = newActivity("PUBLISHED");

        // When / Then : ne doit lever aucune exception.
        activityLikeRepository.deleteByUserIdAndActivityId(user.id(), activity.id());
    }

    @Test
    void findLikedActivityIds_ShouldReturnOnlyLikedOnesAmongCandidates() {
        // Given
        User user = newUser();
        Activity liked = newActivity("PUBLISHED");
        Activity notLiked = newActivity("PUBLISHED");
        activityLikeRepository.save(new ActivityLike(null, user.id(), liked.id(), LocalDateTime.now()));

        // When
        List<Long> result = activityLikeRepository.findLikedActivityIds(
                user.id(), List.of(liked.id(), notLiked.id()));

        // Then
        assertThat(result).containsExactly(liked.id());
    }

    @Test
    void findLikedByUserId_ShouldReturnLikedActivity_WhenPublished() {
        // Given
        User user = newUser();
        Activity published = newActivity("PUBLISHED");
        activityLikeRepository.save(new ActivityLike(null, user.id(), published.id(), LocalDateTime.now()));

        // When
        List<Activity> result = activityRepository.findLikedByUserId(user.id());

        // Then
        assertThat(result).extracting(Activity::id).contains(published.id());
    }

    @Test
    void findLikedByUserId_ShouldExcludeLikedActivity_WhenNotPublished() {
        // Given : critère d'acceptation LS-002 -- une activité annulée (ou tout
        // statut non PUBLISHED) liée ne doit plus apparaître comme active, même si
        // le like lui-même reste en base (voir la javadoc de
        // ActivityRepository#findLikedByUserId).
        User user = newUser();
        Activity cancelled = newActivity("CANCELLED");
        activityLikeRepository.save(new ActivityLike(null, user.id(), cancelled.id(), LocalDateTime.now()));

        // When
        List<Activity> result = activityRepository.findLikedByUserId(user.id());

        // Then
        assertThat(result).extracting(Activity::id).doesNotContain(cancelled.id());
        // Le like lui-même n'est pas supprimé pour autant.
        assertThat(activityLikeRepository.existsByUserIdAndActivityId(user.id(), cancelled.id())).isTrue();
    }

    @Test
    void findLikedByUserId_ShouldOrderByMostRecentlyLikedFirst() {
        // Given
        User user = newUser();
        Activity likedFirst = newActivity("PUBLISHED");
        Activity likedSecond = newActivity("PUBLISHED");
        activityLikeRepository.save(
                new ActivityLike(null, user.id(), likedFirst.id(), LocalDateTime.now().minusHours(1)));
        activityLikeRepository.save(new ActivityLike(null, user.id(), likedSecond.id(), LocalDateTime.now()));

        // When
        List<Activity> result = activityRepository.findLikedByUserId(user.id());

        // Then
        assertThat(result.get(0).id()).isEqualTo(likedSecond.id());
    }
}
