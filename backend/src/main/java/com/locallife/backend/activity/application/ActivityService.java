package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Service Activity — simple délégation vers le repository, avec géocodage
 * de l'adresse à la création (LL-3012).
 */
@Service
public class ActivityService {

    /** Contrat LL-4001 : rayon maximal autorisé pour la recherche géographique. */
    private static final double MAX_RADIUS_KM = 50;

    /**
     * Statuts connus d'une activité. Pas d'enum dédié dans le domaine à ce
     * stade (le champ {@code status} d'{@link Activity} reste une simple
     * chaîne, comme partout ailleurs dans le projet) : cette liste sert
     * uniquement à valider le paramètre {@code status} de la recherche
     * géographique (LL-4003), conformément au contrat LL-4001.
     */
    private static final Set<String> KNOWN_STATUSES = Set.of("PENDING", "PUBLISHED");

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
     * Recherche géographique (LL-4002/LL-4003) : activités situées à moins
     * de {@code radius} kilomètres du point donné, triées par distance
     * croissante, avec filtre optionnel par statut. Reçoit les paramètres
     * bruts (chaînes, tels que fournis par la query string) et fait toute
     * la validation ici plutôt que de s'appuyer sur la coercition
     * automatique de Spring MVC ({@code @RequestParam(required = true)}) :
     * {@link com.locallife.backend.common.GlobalExceptionHandler} attrape
     * actuellement {@code Exception} de façon générique et renverrait 500
     * (au lieu de 400) sur un paramètre manquant/invalide si on laissait
     * Spring lever l'exception lui-même — même choix de conception que
     * {@link GeocodingService} (LL-3012) : validation locale, exceptions
     * {@link IllegalArgumentException} attrapées par le contrôleur.
     *
     * @throws IllegalArgumentException si un paramètre obligatoire est
     *         manquant/non numérique, hors des contraintes du contrat
     *         LL-4001 (latitude/longitude hors plage, rayon ≤ 0 ou
     *         &gt; 50 km), ou si {@code status} ne correspond à aucune
     *         valeur connue.
     */
    public List<Activity> findNearby(String latitudeRaw, String longitudeRaw, String radiusRaw, String status) {
        double latitude = parseRequiredDouble("latitude", latitudeRaw);
        double longitude = parseRequiredDouble("longitude", longitudeRaw);
        double radiusKm = parseRequiredDouble("radius", radiusRaw);

        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Le paramètre 'latitude' doit être compris entre -90 et 90.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Le paramètre 'longitude' doit être compris entre -180 et 180.");
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException(
                    "Le paramètre 'radius' doit être strictement positif et ne pas dépasser " + (int) MAX_RADIUS_KM
                            + " km.");
        }
        if (status != null && !KNOWN_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Le paramètre 'status' ne correspond à aucune valeur connue.");
        }

        double radiusMeters = radiusKm * 1000;
        return activityRepository.findWithinRadius(latitude, longitude, radiusMeters, status);
    }

    private double parseRequiredDouble(String paramName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Le paramètre '" + paramName + "' est obligatoire.");
        }
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Le paramètre '" + paramName + "' doit être un nombre.");
        }
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
