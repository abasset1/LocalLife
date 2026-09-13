package com.locallife.backend.link.infrastructure;

import com.locallife.backend.link.domain.ActivityLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityLink (LL-11008). Même patron que les autres
 * repositories du sprint : étend {@link Repository} (interface
 * marqueur), n'expose que les méthodes listées ici.
 * {@link #findByActivityId} sert le critère d'acceptation « plusieurs
 * liens possibles ».
 */
public interface ActivityLinkRepository extends Repository<ActivityLink, Long> {

    List<ActivityLink> findByActivityId(Long activityId);

    Optional<ActivityLink> findById(Long id);

    ActivityLink save(ActivityLink activityLink);

    /**
     * Supprime les liens d'une activité (LL-11011) : utilisé par
     * {@code ImportService} avant de reconstruire les liens à chaque
     * réimport.
     */
    void deleteByActivityId(Long activityId);
}
