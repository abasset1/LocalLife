package com.locallife.backend.metadata.infrastructure;

import com.locallife.backend.metadata.domain.ActivityMetadata;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityMetadata (LL-11009). Même patron que les autres
 * repositories du sprint.
 */
public interface ActivityMetadataRepository extends Repository<ActivityMetadata, Long> {

    Optional<ActivityMetadata> findByActivityId(Long activityId);

    Optional<ActivityMetadata> findById(Long id);

    ActivityMetadata save(ActivityMetadata activityMetadata);
}
