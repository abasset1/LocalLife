package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivityPrimaryCategory;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityPrimaryCategory (CAT-006). Relation 0..1 : lecture
 * par {@link #findByActivityId}, écriture en remplacement complet par le
 * service appelant ({@code deleteByActivityId} puis {@code save}), même
 * patron que {@code ActivityTagRepository} (LL-11009).
 */
public interface ActivityPrimaryCategoryRepository extends Repository<ActivityPrimaryCategory, Long> {

    Optional<ActivityPrimaryCategory> findByActivityId(Long activityId);

    ActivityPrimaryCategory save(ActivityPrimaryCategory activityPrimaryCategory);

    void deleteByActivityId(Long activityId);

}
