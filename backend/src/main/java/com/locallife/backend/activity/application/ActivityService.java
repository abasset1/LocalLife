package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Activity — minimal, simple délégation vers le repository.
 */
@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public List<Activity> findAll() {
        return activityRepository.findAll();
    }

    public Optional<Activity> findById(Long id) {
        return activityRepository.findById(id);
    }

}
