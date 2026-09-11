package com.locallife.backend.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.infrastructure.ActivityMediaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la délégation de {@link ActivityMediaService} vers
 * {@link ActivityMediaRepository} (LL-11007).
 */
@ExtendWith(MockitoExtension.class)
class ActivityMediaServiceTest {

    @Mock
    private ActivityMediaRepository activityMediaRepository;

    private ActivityMediaService activityMediaService;

    @BeforeEach
    void setUp() {
        activityMediaService = new ActivityMediaService(activityMediaRepository);
    }

    @Test
    void attach_ShouldDelegateToRepository() {
        ActivityMedia toAttach = new ActivityMedia(null, 1L, 10L, 0);
        ActivityMedia saved = new ActivityMedia(1L, 1L, 10L, 0);
        when(activityMediaRepository.save(toAttach)).thenReturn(saved);

        ActivityMedia result = activityMediaService.attach(toAttach);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findByActivityId_ShouldReturnMediaInPositionOrder() {
        // Critère d'acceptation « ordre conservé » : ce test vérifie que le service renvoie
        // tel quel ce que le repository fournit déjà trié (voir
        // ActivityMediaRepository#findByActivityIdOrderByPosition) — l'ordre réel est garanti
        // côté requête, pas recalculé ici.
        ActivityMedia first = new ActivityMedia(1L, 42L, 10L, 0);
        ActivityMedia second = new ActivityMedia(2L, 42L, 11L, 1);
        when(activityMediaRepository.findByActivityIdOrderByPosition(42L)).thenReturn(List.of(first, second));

        List<ActivityMedia> result = activityMediaService.findByActivityId(42L);

        assertThat(result).containsExactly(first, second);
    }
}
