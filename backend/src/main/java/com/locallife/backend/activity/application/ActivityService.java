package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Activity — simple délégation vers le repository, avec géocodage
 * de l'adresse à la création (LL-3012).
 */
@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final GeocodingService geocodingService;

    public ActivityService(ActivityRepository activityRepository, GeocodingService geocodingService) {
        this.activityRepository = activityRepository;
        this.geocodingService = geocodingService;
    }

    public List<Activity> findAll() {
        return activityRepository.findAll();
    }

    public Optional<Activity> findById(Long id) {
        return activityRepository.findById(id);
    }

    /**
     * Recherche géographique (LL-4002) : activités situées à moins de
     * {@code radiusKm} kilomètres du point donné, triées par distance
     * croissante. {@code radiusKm} est exprimé en kilomètres conformément
     * au contrat LL-4001 ; converti en mètres ici avant l'appel au
     * repository, car {@code ST_DWithin} (PostGIS, type {@code geography})
     * attend une distance en mètres.
     *
     * Pas de filtrage par statut à ce stade (hors périmètre de LL-4002,
     * prévu en LL-4003 lors de la création de l'endpoint).
     */
    public List<Activity> findNearby(double latitude, double longitude, double radiusKm) {
        double radiusMeters = radiusKm * 1000;
        return activityRepository.findWithinRadius(latitude, longitude, radiusMeters);
    }

    /**
     * Crée une activité à partir d'une contribution (LL-2012, adresse
     * géocodée depuis LL-3012). L'adresse elle-même n'est pas conservée en
     * base, seules les coordonnées obtenues via {@link GeocodingService} le
     * sont. Statut par défaut : {@code PENDING} (pas de système de
     * modération à ce stade, à ajuster si besoin). Aucune date de
     * début/fin n'est demandée par le formulaire de contribution ; la date
     * de soumission est utilisée comme {@code startDate} en attendant un
     * futur ticket sur ce point.
     */
    public Activity createActivity(String title, String description, String category, String address) {
        Coordinates coordinates = geocodingService.geocode(address);
        Activity activity = new Activity(
                null, title, description, category,
                coordinates.latitude(), coordinates.longitude(), LocalDateTime.now(), null, "PENDING");
        return activityRepository.save(activity);
    }

}
