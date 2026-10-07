package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityActivityType (CAT-006). Même patron que
 * {@link ActivityCategoryRepository}.
 */
public interface ActivityActivityTypeRepository extends Repository<ActivityActivityType, Long> {

    List<ActivityActivityType> findByActivityId(Long activityId);

    ActivityActivityType save(ActivityActivityType activityActivityType);

    void deleteByActivityId(Long activityId);

}
