package com.locallife.backend.like.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.like.domain.ActivityLike;
import com.locallife.backend.like.infrastructure.ActivityLikeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private ActivityLikeRepository activityLikeRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private LikeService likeService;

    private Activity activity(long id) {
        return new Activity(id, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null, "PUBLISHED",
                1L, null, null, null, null, null, null, null, null, null, null,
                1L);
    }

    @Test
    void like_ShouldSaveNewLike_WhenNotAlreadyLiked() {
        // Given
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity(1L)));
        when(activityLikeRepository.existsByUserIdAndActivityId(42L, 1L)).thenReturn(false);

        // When
        boolean result = likeService.like(42L, 1L);

        // Then
        assertThat(result).isTrue();
        verify(activityLikeRepository).save(any(ActivityLike.class));
    }

    @Test
    void like_ShouldBeIdempotent_WhenAlreadyLiked() {
        // Given : critère d'acceptation "un utilisateur ne peut pas créer plusieurs
        // likes pour le même événement" — un second appel ne doit ni échouer ni
        // dupliquer, seulement confirmer l'état déjà liké.
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity(1L)));
        when(activityLikeRepository.existsByUserIdAndActivityId(42L, 1L)).thenReturn(true);

        // When
        boolean result = likeService.like(42L, 1L);

        // Then
        assertThat(result).isTrue();
        verify(activityLikeRepository, never()).save(any());
    }

    @Test
    void like_ShouldReturnFalse_WhenActivityDoesNotExist() {
        // Given
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        boolean result = likeService.like(42L, 99L);

        // Then
        assertThat(result).isFalse();
        verifyNoInteractions(activityLikeRepository);
    }

    @Test
    void unlike_ShouldDeleteLike() {
        // When
        likeService.unlike(42L, 1L);

        // Then
        verify(activityLikeRepository).deleteByUserIdAndActivityId(42L, 1L);
    }

    @Test
    void unlike_ShouldNotCheckActivityExistence() {
        // Voir la javadoc de LikeService#unlike : un aller-retour vers
        // ActivityRepository serait inutile ici.
        likeService.unlike(42L, 1L);

        verifyNoInteractions(activityRepository);
    }

    @Test
    void getLikedActivities_ShouldDelegateToRepository() {
        // Given
        List<Activity> liked = List.of(activity(1L), activity(2L));
        when(activityRepository.findLikedByUserId(42L)).thenReturn(liked);

        // When
        List<Activity> result = likeService.getLikedActivities(42L);

        // Then
        assertThat(result).isEqualTo(liked);
    }

    @Test
    void findLikedActivityIds_ShouldReturnEmptyList_WithoutQuerying_WhenActivityIdsListIsEmpty() {
        // Given / When
        List<Long> result = likeService.findLikedActivityIds(42L, List.of());

        // Then : évite une requête SQL inutile pour une liste d'activités vide
        // (ex. une page de résultats /nearby sans aucun résultat).
        assertThat(result).isEmpty();
        verifyNoInteractions(activityLikeRepository);
    }

    @Test
    void findLikedActivityIds_ShouldDelegateToRepository_WhenActivityIdsProvided() {
        // Given
        when(activityLikeRepository.findLikedActivityIds(42L, List.of(1L, 2L, 3L))).thenReturn(List.of(1L, 3L));

        // When
        List<Long> result = likeService.findLikedActivityIds(42L, List.of(1L, 2L, 3L));

        // Then
        assertThat(result).containsExactly(1L, 3L);
    }
}
