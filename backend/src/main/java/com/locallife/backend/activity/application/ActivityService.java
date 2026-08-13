package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
     * Recherche géographique (LL-4002/LL-4003/LL-4004) : activités situées
     * à moins de {@code radius} kilomètres du point donné, triées par
     * distance croissante, avec filtres optionnels par statut et par
     * catégorie. Reçoit les paramètres bruts (chaînes, tels que fournis par
     * la query string) et fait toute la validation ici plutôt que de
     * s'appuyer sur la coercition automatique de Spring MVC
     * ({@code @RequestParam(required = true)}) :
     * {@link com.locallife.backend.common.GlobalExceptionHandler} attrape
     * actuellement {@code Exception} de façon générique et renverrait 500
     * (au lieu de 400) sur un paramètre manquant/invalide si on laissait
     * Spring lever l'exception lui-même — même choix de conception que
     * {@link GeocodingService} (LL-3012) : validation locale, exceptions
     * {@link IllegalArgumentException} attrapées par le contrôleur.
     *
     * {@code category} (LL-4004) : liste de catégories séparées par des
     * virgules (ex. {@code "concert,marché"}), ou {@code null} pour ne pas
     * filtrer. Chaque valeur est nettoyée (espaces retirés, valeurs vides
     * ignorées). Comparée telle quelle à la colonne {@code category}
     * d'{@link Activity}, qui est une chaîne libre saisie par le
     * contributeur (voir {@code createActivity} ci-dessous) — il n'existe
     * aucun lien entre cette colonne et la table {@code category}
     * (celle-ci n'a ni FK depuis {@code activity}, ni données, et
     * l'exemple du ticket LL-4004 utilisant {@code categoryId} ne
     * correspond donc à aucune donnée réelle actuellement). ⚠️ Décision à
     * valider : le paramètre s'appelle ici {@code category} (chaîne), pas
     * {@code categoryId}, pour rester honnête vis-à-vis du modèle de
     * données actuel ; introduire une vraie relation {@code Activity} →
     * {@code Category} serait une modification du modèle métier hors
     * périmètre de ce ticket (interdit explicitement par les règles du
     * Sprint 4). Aucune catégorie n'étant une valeur "invalide" en soi
     * (champ libre à la création), une catégorie qui ne correspond à
     * aucune activité renvoie simplement une liste vide, pas d'erreur 400.
     *
     * {@code date} (LL-4005) : date unique au format ISO-8601
     * ({@code yyyy-MM-dd}), ou {@code null} pour ne pas filtrer. Une
     * activité est retenue quand cette date tombe dans sa période
     * {@code [startDate, endDate]} (bornes incluses, comparaison au jour
     * près — l'heure de {@code startDate}/{@code endDate} n'entre pas en
     * jeu). Couvre à la fois les activités d'une seule journée et celles
     * s'étalant sur plusieurs jours. {@code endDate} peut être absent en
     * base (activités créées via le formulaire de contribution, voir
     * {@code createActivity} ci-dessous, qui ne renseigne pas de date de
     * fin) : dans ce cas l'activité est traitée comme ne durant que la
     * journée de {@code startDate}, voir
     * {@link ActivityRepository#findWithinRadius} pour le détail SQL.
     *
     * @throws IllegalArgumentException si un paramètre obligatoire est
     *         manquant/non numérique, hors des contraintes du contrat
     *         LL-4001 (latitude/longitude hors plage, rayon ≤ 0 ou
     *         &gt; 50 km), si {@code status} ne correspond à aucune
     *         valeur connue, ou si {@code date} n'est pas au format
     *         ISO-8601 ({@code yyyy-MM-dd}).
     */
    public List<Activity> findNearby(
            String latitudeRaw, String longitudeRaw, String radiusRaw, String status, String category,
            String dateRaw) {
        double latitude = parseRequiredDouble("latitude", latitudeRaw);
        double longitude = parseRequiredDouble("longitude", longitudeRaw);
        double radiusKm = parseRequiredDouble("radius", radiusRaw);

        validateLatitude("latitude", latitude);
        validateLongitude("longitude", longitude);
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException(
                    "Le paramètre 'radius' doit être strictement positif et ne pas dépasser " + (int) MAX_RADIUS_KM
                            + " km.");
        }
        if (status != null && !KNOWN_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Le paramètre 'status' ne correspond à aucune valeur connue.");
        }
        LocalDate date = parseOptionalDate(dateRaw);

        double radiusMeters = radiusKm * 1000;
        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinRadius(latitude, longitude, radiusMeters, status, categoriesCsv, date);
    }

    /**
     * Recherche par zone cartographique (LL-4006/LL-4007) : activités
     * situées à l'intérieur du rectangle défini par les coins sud-ouest
     * ({@code swLatitude}/{@code swLongitude}) et nord-est
     * ({@code neLatitude}/{@code neLongitude}), avec les mêmes filtres
     * optionnels par statut, catégorie et date que {@link #findNearby}.
     * Voir le contrat détaillé dans
     * {@code docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md}. Même
     * approche de validation locale que {@link #findNearby} (paramètres
     * reçus en {@code String}, exceptions {@link IllegalArgumentException}
     * attrapées par le contrôleur), pour la même raison
     * ({@link com.locallife.backend.common.GlobalExceptionHandler}
     * renverrait {@code 500} au lieu de {@code 400} sur un paramètre
     * manquant/invalide sinon).
     *
     * Pas de tri par distance ici : il n'y a pas de point de référence
     * unique pour une zone rectangulaire (décision du contrat LL-4006) —
     * résultats triés par {@code id} croissant, voir
     * {@link ActivityRepository#findWithinBounds}.
     *
     * @throws IllegalArgumentException si un paramètre obligatoire est
     *         manquant/non numérique, si une latitude/longitude est hors
     *         plage (-90/90, -180/180), si {@code swLatitude >=
     *         neLatitude} ou {@code swLongitude >= neLongitude} (contrat
     *         LL-4006 : la traversée de l'antiméridien n'est pas
     *         supportée), si {@code status} ne correspond à aucune valeur
     *         connue, ou si {@code date} n'est pas au format ISO-8601.
     */
    public List<Activity> findWithinBounds(
            String swLatitudeRaw, String swLongitudeRaw, String neLatitudeRaw, String neLongitudeRaw,
            String status, String category, String dateRaw) {
        double swLatitude = parseRequiredDouble("swLatitude", swLatitudeRaw);
        double swLongitude = parseRequiredDouble("swLongitude", swLongitudeRaw);
        double neLatitude = parseRequiredDouble("neLatitude", neLatitudeRaw);
        double neLongitude = parseRequiredDouble("neLongitude", neLongitudeRaw);

        validateLatitude("swLatitude", swLatitude);
        validateLongitude("swLongitude", swLongitude);
        validateLatitude("neLatitude", neLatitude);
        validateLongitude("neLongitude", neLongitude);

        if (swLatitude >= neLatitude) {
            throw new IllegalArgumentException(
                    "Le paramètre 'swLatitude' doit être strictement inférieur à 'neLatitude'.");
        }
        if (swLongitude >= neLongitude) {
            throw new IllegalArgumentException(
                    "Le paramètre 'swLongitude' doit être strictement inférieur à 'neLongitude' "
                            + "(la traversée de l'antiméridien n'est pas supportée).");
        }
        if (status != null && !KNOWN_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Le paramètre 'status' ne correspond à aucune valeur connue.");
        }
        LocalDate date = parseOptionalDate(dateRaw);

        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinBounds(
                swLatitude, swLongitude, neLatitude, neLongitude, status, categoriesCsv, date);
    }

    private void validateLatitude(String paramName, double latitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Le paramètre '" + paramName + "' doit être compris entre -90 et 90.");
        }
    }

    private void validateLongitude(String paramName, double longitude) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Le paramètre '" + paramName + "' doit être compris entre -180 et 180.");
        }
    }

    private LocalDate parseOptionalDate(String dateRaw) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateRaw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Le paramètre 'date' doit être au format ISO-8601 (yyyy-MM-dd).");
        }
    }

    private String normalizeCategories(String categoryRaw) {
        if (categoryRaw == null) {
            return null;
        }
        String cleaned = Arrays.stream(categoryRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(","));
        return cleaned.isEmpty() ? null : cleaned;
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
