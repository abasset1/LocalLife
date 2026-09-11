package com.locallife.backend.tag.application;

import com.locallife.backend.tag.domain.ActivityTag;
import com.locallife.backend.tag.infrastructure.ActivityTagRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service ActivityTag (LL-11009). {@link #create} refuse un tag vide :
 * seul garde-fou nécessaire, l'unicité (pas deux fois le même tag sur la
 * même activité) et l'intégrité référentielle sont garanties par les
 * contraintes de {@code V24__create_accessibility_tag_metadata_tables.sql}.
 */
@Service
public class ActivityTagService {

    private final ActivityTagRepository activityTagRepository;

    public ActivityTagService(ActivityTagRepository activityTagRepository) {
        this.activityTagRepository = activityTagRepository;
    }

    public List<ActivityTag> findByActivityId(Long activityId) {
        return activityTagRepository.findByActivityId(activityId);
    }

    /**
     * @throws IllegalArgumentException si {@code activityTag.tag()} est absent ou vide
     */
    public ActivityTag create(ActivityTag activityTag) {
        if (activityTag.tag() == null || activityTag.tag().isBlank()) {
            throw new IllegalArgumentException("Un tag ne peut pas être vide.");
        }
        return activityTagRepository.save(activityTag);
    }
}
