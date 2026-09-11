package com.locallife.backend.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.tag.domain.ActivityTag;
import com.locallife.backend.tag.infrastructure.ActivityTagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre le garde-fou de {@link ActivityTagService#create} (LL-11009)
 * et la délégation de {@link ActivityTagService#findByActivityId}.
 */
@ExtendWith(MockitoExtension.class)
class ActivityTagServiceTest {

    @Mock
    private ActivityTagRepository activityTagRepository;

    private ActivityTagService activityTagService;

    @BeforeEach
    void setUp() {
        activityTagService = new ActivityTagService(activityTagRepository);
    }

    @Test
    void create_ShouldThrow_WhenTagIsAbsent() {
        ActivityTag withoutTag = new ActivityTag(null, 1L, null);

        assertThatThrownBy(() -> activityTagService.create(withoutTag)).isInstanceOf(IllegalArgumentException.class);

        verify(activityTagRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrow_WhenTagIsBlank() {
        ActivityTag blankTag = new ActivityTag(null, 1L, "   ");

        assertThatThrownBy(() -> activityTagService.create(blankTag)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldSave_WhenTagIsPresent() {
        ActivityTag toCreate = new ActivityTag(null, 1L, "famille");
        ActivityTag saved = new ActivityTag(1L, 1L, "famille");
        when(activityTagRepository.save(toCreate)).thenReturn(saved);

        ActivityTag result = activityTagService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findByActivityId_ShouldReturnAllTagsForActivity() {
        // Critère d'acceptation « plusieurs tags par activité ».
        ActivityTag first = new ActivityTag(1L, 42L, "famille");
        ActivityTag second = new ActivityTag(2L, 42L, "gratuit");
        when(activityTagRepository.findByActivityId(42L)).thenReturn(List.of(first, second));

        List<ActivityTag> result = activityTagService.findByActivityId(42L);

        assertThat(result).containsExactly(first, second);
    }
}
