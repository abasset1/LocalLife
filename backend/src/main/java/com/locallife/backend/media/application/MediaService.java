package com.locallife.backend.media.application;

import com.locallife.backend.media.domain.Media;
import com.locallife.backend.media.infrastructure.MediaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Media (LL-11007). Délégation simple vers le repository, même
 * patron que {@code LocationService} pour ses méthodes de base.
 *
 * {@link #create} refuse un média sans {@code url} : seule donnée
 * réellement indispensable (voir la javadoc de {@link Media}), même
 * principe que le garde-fou de {@code LocationService#create} (LL-11001)
 * pour une donnée insuffisante.
 */
@Service
public class MediaService {

    private final MediaRepository mediaRepository;

    public MediaService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public List<Media> findAll() {
        return mediaRepository.findAll();
    }

    public Optional<Media> findById(Long id) {
        return mediaRepository.findById(id);
    }

    /**
     * @throws IllegalArgumentException si {@code media.url()} est absente ou vide
     */
    public Media create(Media media) {
        if (media.url() == null || media.url().isBlank()) {
            throw new IllegalArgumentException("Un média doit avoir une url.");
        }
        return mediaRepository.save(media);
    }
}
