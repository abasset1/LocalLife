package com.locallife.backend.activity.application;

import com.locallife.backend.activity.infrastructure.ActivityHostPlaceRepository;
import com.locallife.backend.activity.infrastructure.ActivityHostPlaceRepository.HostPlace;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Activité proposée par un lieu (décision du 22/10/2026) : un professionnel rattache sa contribution à un lieu
 * publié qu'il gère ; la fiche affiche « proposé par » tant qu'il le gère. La modération de l'activité reste la
 * même que pour toute contribution.
 */
@Service
public class ActivityHostPlaceService {

    private final ActivityHostPlaceRepository repository;

    public ActivityHostPlaceService(ActivityHostPlaceRepository repository) {
        this.repository = repository;
    }

    /**
     * À appeler avant toute écriture.
     *
     * @throws IllegalArgumentException lieu non publié ou non géré par {@code userId}
     */
    public void validate(Long userId, Long hostPlaceId) {
        if (hostPlaceId != null && !repository.canHost(userId, hostPlaceId)) {
            throw new IllegalArgumentException("Tu ne peux rattacher une activité qu'à un lieu publié que tu gères.");
        }
    }

    public void set(Long activityId, Long hostPlaceId) {
        repository.set(activityId, hostPlaceId);
    }

    public Optional<Long> findChosen(Long activityId) {
        return repository.findChosen(activityId);
    }

    public HostPlace findDisplayed(Long activityId) {
        return repository.findDisplayed(activityId).orElse(null);
    }
}
