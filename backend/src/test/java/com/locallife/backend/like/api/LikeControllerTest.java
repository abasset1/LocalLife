package com.locallife.backend.like.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.activity.infrastructure.ActivityNextOccurrenceRepository;
import com.locallife.backend.activity.api.ActivityResponse;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.user.domain.Role;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeControllerTest {

    @Mock
    private LikeService likeService;

    @Mock
    private SourceService sourceService;

    @Mock
    private ActivityMediaService activityMediaService;

    @Mock
    private MediaService mediaService;

    @Mock
    private ActivityCoordinatePrecisionRepository coordinatePrecisionRepository;

    @Mock
    private ActivityNextOccurrenceRepository nextOccurrenceRepository;

    @InjectMocks
    private LikeController likeController;

    private final JwtAuthentication authentication = new JwtAuthentication(42L, "alice@example.com", Role.USER);

    @Test
    void getLikedActivities_ShouldReturnActivitiesWithLikedByCurrentUserTrue() {
        // Given
        Activity liked = new Activity(1L, "Concert", "desc", "concert", 43.29, 5.37, LocalDateTime.now(), null,
                "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        Source source = new Source(1L, "MANUAL", "MANUAL", null, "ACTIVE", null, null, null);
        when(likeService.getLikedActivities(42L)).thenReturn(List.of(liked));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        List<ActivityResponse> result = likeController.getLikedActivities(authentication);

        // Then : voir ActivityResponse.from(activity, sourceName, createdByUsername, likedByCurrentUser)
        // — toujours true ici, par construction (voir la javadoc de LikeController).
        assertEquals(List.of(ActivityResponse.from(liked, "MANUAL", null, true)), result);
    }

    @Test
    void getLikedActivities_ShouldReturnEmptyList_WhenNoLikes() {
        // Given
        when(likeService.getLikedActivities(42L)).thenReturn(List.of());
        when(sourceService.getAllSources()).thenReturn(List.of());

        // When
        List<ActivityResponse> result = likeController.getLikedActivities(authentication);

        // Then
        assertEquals(List.of(), result);
    }

    @Test
    void getLikedActivities_ShouldUseCurrentUserIdFromJwt_NotFromRequest() {
        // Même garantie que UserController#getCurrentUser (LL-EF-006) : l'id provient
        // exclusivement du JWT.
        when(likeService.getLikedActivities(42L)).thenReturn(List.of());
        when(sourceService.getAllSources()).thenReturn(List.of());

        likeController.getLikedActivities(authentication);

        org.mockito.Mockito.verify(likeService).getLikedActivities(42L);
    }
}
