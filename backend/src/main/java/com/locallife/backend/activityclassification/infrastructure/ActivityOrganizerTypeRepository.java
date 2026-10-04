package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityOrganizerType (CAT-006). Relation 0..1 : même
 * patron que {@code ActivitySecondaryCategoryRepository} (relation 0..1 : lecture
 * par {@code findByActivityId}, écriture en remplacement complet).
 */
public interface ActivityOrganizerTypeRepository extends Repository<ActivityOrganizerType, Long> {

    Optional<ActivityOrganizerType> findByActivityId(Long activityId);

    ActivityOrganizerType save(ActivityOrganizerType activityOrganizerType);

    void deleteByActivityId(Long activityId);

}
