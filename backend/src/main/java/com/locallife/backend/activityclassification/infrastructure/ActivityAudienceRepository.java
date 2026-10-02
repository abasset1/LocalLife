package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivityAudience;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityAudience (CAT-006). Même patron que
 * {@link ActivitySecondaryCategoryRepository}.
 */
public interface ActivityAudienceRepository extends Repository<ActivityAudience, Long> {

    List<ActivityAudience> findByActivityId(Long activityId);

    ActivityAudience save(ActivityAudience activityAudience);

    void deleteByActivityId(Long activityId);

}
