package com.locallife.backend.media.infrastructure;

import com.locallife.backend.media.domain.Media;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Media (LL-11007). Même patron que
 * {@code LocationRepository}/{@code ScheduleRepository} : étend
 * {@link Repository} (interface marqueur), n'expose que les méthodes
 * listées ici.
 */
public interface MediaRepository extends Repository<Media, Long> {

    List<Media> findAll();

    Optional<Media> findById(Long id);

    Media save(Media media);
}
