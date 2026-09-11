package com.locallife.backend.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.media.domain.Media;
import com.locallife.backend.media.infrastructure.MediaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre le garde-fou de {@link MediaService#create} (LL-11007, « url
 * source conservée » implique une url réellement présente) et la
 * délégation des autres méthodes.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository mediaRepository;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(mediaRepository);
    }

    private Media media(Long id) {
        return new Media(id, "https://example.com/photo.jpg", "IMAGE", 1200, 800, "Ville d'Avignon", "Le pont");
    }

    @Test
    void create_ShouldThrow_WhenUrlIsAbsent() {
        Media withoutUrl = new Media(null, null, "IMAGE", null, null, null, null);

        assertThatThrownBy(() -> mediaService.create(withoutUrl)).isInstanceOf(IllegalArgumentException.class);

        verify(mediaRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrow_WhenUrlIsBlank() {
        Media blankUrl = new Media(null, "   ", "IMAGE", null, null, null, null);

        assertThatThrownBy(() -> mediaService.create(blankUrl)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_ShouldSave_WhenUrlIsPresent() {
        Media toCreate = media(null);
        Media saved = media(1L);
        when(mediaRepository.save(toCreate)).thenReturn(saved);

        Media result = mediaService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findById_ShouldDelegateToRepository() {
        Media found = media(1L);
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(found));

        Optional<Media> result = mediaService.findById(1L);

        assertThat(result).contains(found);
    }

    @Test
    void findAll_ShouldDelegateToRepository() {
        Media found = media(1L);
        when(mediaRepository.findAll()).thenReturn(List.of(found));

        List<Media> result = mediaService.findAll();

        assertThat(result).containsExactly(found);
    }
}
