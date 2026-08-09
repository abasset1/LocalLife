package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import java.time.LocalDateTime;
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

    /**
     * Crée une activité à partir d'une contribution (LL-2012). Statut par
     * défaut : {@code PENDING} (pas de système de modération à ce stade,
     * à ajuster si besoin). Aucune date de début/fin n'est demandée par le
     * formulaire de contribution ; la date de soumission est utilisée
     * comme {@code startDate} en attendant un futur ticket sur ce point.
     */
    public Activity createActivity(String title, String description, String category, double latitude, double longitude) {
        Activity activity = new Activity(null, title, description, category, latitude, longitude, LocalDateTime.now(), null, "PENDING");
        return activityRepository.save(activity);
    }

}
