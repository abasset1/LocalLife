package com.locallife.backend.media.infrastructure;

import com.locallife.backend.media.domain.ActivityMedia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityMedia (LL-11007). Même patron que les autres
 * repositories du sprint. {@link #findByActivityIdOrderByPosition} sert
 * directement le critère d'acceptation « ordre conservé » — Spring Data
 * JDBC dérive la clause {@code ORDER BY position} du nom de la méthode
 * (mot-clé {@code OrderBy}), pas d'ordre implicite/d'insertion à ne pas
 * garantir soi-même.
 */
public interface ActivityMediaRepository extends Repository<ActivityMedia, Long> {

    List<ActivityMedia> findByActivityIdOrderByPosition(Long activityId);

    Optional<ActivityMedia> findById(Long id);

    ActivityMedia save(ActivityMedia activityMedia);
}
