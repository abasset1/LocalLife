package com.locallife.backend.tag.infrastructure;

import com.locallife.backend.tag.domain.ActivityTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityTag (LL-11009). Même patron que les autres
 * repositories du sprint. {@link #findByActivityId} sert le critère
 * d'acceptation « plusieurs tags par activité ».
 */
public interface ActivityTagRepository extends Repository<ActivityTag, Long> {

    List<ActivityTag> findByActivityId(Long activityId);

    Optional<ActivityTag> findById(Long id);

    ActivityTag save(ActivityTag activityTag);
}
