package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivitySecondaryCategory;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivitySecondaryCategory (CAT-006). Relation 0..N : même
 * patron que {@code ActivityTagRepository} (LL-11009) — lecture par
 * {@link #findByActivityId}, remplacement complet géré par le service
 * ({@code deleteByActivityId} puis une {@code save} par élément).
 */
public interface ActivitySecondaryCategoryRepository extends Repository<ActivitySecondaryCategory, Long> {

    List<ActivitySecondaryCategory> findByActivityId(Long activityId);

    ActivitySecondaryCategory save(ActivitySecondaryCategory activitySecondaryCategory);

    void deleteByActivityId(Long activityId);

}
