package com.locallife.backend.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.media.domain.Media;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration contre la base réelle (LL-11007), même patron que
 * {@code LocationRepositoryIntegrationTest} : chaque test est englobé
 * dans une transaction annulée à la fin.
 */
@SpringBootTest
@Transactional
class MediaRepositoryIntegrationTest {

    @Autowired
    private MediaRepository mediaRepository;

    private Media testMedia() {
        return new Media(null, "https://example.com/photo.jpg", "IMAGE", 1200, 800, "Ville d'Avignon", "Le pont");
    }

    @Test
    void save_ShouldPersistAndAssignId() {
        Media saved = mediaRepository.save(testMedia());

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findById_ShouldReturnSavedMedia_WithAllFieldsIntact() {
        Media media = testMedia();

        Media saved = mediaRepository.save(media);
        Optional<Media> found = mediaRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().url()).isEqualTo(media.url());
        assertThat(found.get().type()).isEqualTo(media.type());
        assertThat(found.get().width()).isEqualTo(media.width());
        assertThat(found.get().height()).isEqualTo(media.height());
        assertThat(found.get().credit()).isEqualTo(media.credit());
        assertThat(found.get().altText()).isEqualTo(media.altText());
    }

    @Test
    void save_ShouldSucceed_WhenOnlyUrlIsProvided() {
        // Seule url est indispensable (voir la javadoc de Media) : le reste peut être absent.
        Media minimal = new Media(null, "https://example.com/photo.jpg", null, null, null, null, null);

        Media saved = mediaRepository.save(minimal);

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void findById_ShouldReturnEmpty_WhenMediaDoesNotExist() {
        assertThat(mediaRepository.findById(-1L)).isEmpty();
    }

    @Test
    void findAll_ShouldIncludeSavedMedia() {
        Media saved = mediaRepository.save(testMedia());

        assertThat(mediaRepository.findAll()).extracting(Media::id).contains(saved.id());
    }
}
