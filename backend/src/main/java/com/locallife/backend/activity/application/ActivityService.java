package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
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

    /** Alignée sur la colonne {@code activity.title} (V2__create_activity_table.sql), voir {@code createActivity}. */
    private static final int MAX_TITLE_LENGTH = 255;

    /**
     * Seul statut retourné par les recherches publiques depuis LL-6004 —
     * voir {@link #findNearby} et {@link #findWithinBounds}.
     */
    private static final String PUBLIC_STATUS = "PUBLISHED";

    /**
     * Statuts connus d'une activité, formalisés en LL-6003 — voir la
     * javadoc du champ {@code status} sur {@link Activity} pour le détail
     * des transitions. Pas d'enum dédié dans le domaine à ce stade (le
     * champ {@code status} d'{@link Activity} reste une simple chaîne,
     * comme partout ailleurs dans le projet) : cette liste sert à valider
     * le paramètre {@code status} de {@link #findByStatus} (LL-6005,
     * consultation administrative par statut).
     */
    private static final Set<String> KNOWN_STATUSES = Set.of("PENDING", "PUBLISHED", "REJECTED");

    /**
     * Seul statut de départ autorisé pour {@link #publish}/{@link #reject}
     * (LL-6006) — voir la javadoc du champ {@code status} sur
     * {@link Activity} : les seules transitions prévues en LL-6003 sont
     * {@code PENDING → PUBLISHED} et {@code PENDING → REJECTED}.
     */
    private static final String PENDING_STATUS = "PENDING";

    private final ActivityRepository activityRepository;
    private final GeocodingService geocodingService;
    private final SourceService sourceService;

    public ActivityService(
            ActivityRepository activityRepository, GeocodingService geocodingService, SourceService sourceService) {
        this.activityRepository = activityRepository;
        this.geocodingService = geocodingService;
        this.sourceService = sourceService;
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
     * distance croissante, avec filtres optionnels par catégorie et par
     * date. Reçoit les paramètres bruts (chaînes, tels que fournis par
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
     * Statut (LL-6004) : ce point d'accès est public (aucune
     * authentification requise) — il ne retourne donc que les activités
     * {@code PUBLISHED}, sans exception possible. Jusqu'en LL-6003, un
     * paramètre {@code status} permettait de filtrer sur n'importe quelle
     * valeur connue (y compris {@code PENDING}/{@code REJECTED}) ; ce
     * paramètre a été retiré de l'API publique avec l'introduction de la
     * modération (dette technique signalée en LL-5012/LL-5008 : une
     * activité non publiée restait visible sur la carte faute de filtre
     * par défaut — voir {@code DETTE_TECHNIQUE.md}). Une future
     * consultation par statut (ex. file de modération) passera par un
     * endpoint distinct, réservé aux administrateurs (LL-6005), pas par
     * celui-ci.
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
     *         &gt; 50 km), ou si {@code date} n'est pas au format
     *         ISO-8601 ({@code yyyy-MM-dd}).
     */
    public List<Activity> findNearby(
            String latitudeRaw, String longitudeRaw, String radiusRaw, String category, String dateRaw) {
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
        LocalDate date = parseOptionalDate(dateRaw);

        double radiusMeters = radiusKm * 1000;
        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinRadius(
                latitude, longitude, radiusMeters, PUBLIC_STATUS, categoriesCsv, date);
    }

    /**
     * Recherche par zone cartographique (LL-4006/LL-4007) : activités
     * situées à l'intérieur du rectangle défini par les coins sud-ouest
     * ({@code swLatitude}/{@code swLongitude}) et nord-est
     * ({@code neLatitude}/{@code neLongitude}), avec les mêmes filtres
     * optionnels par catégorie et date que {@link #findNearby} (et la
     * même restriction au statut {@code PUBLISHED}, voir sa javadoc pour
     * le détail — LL-6004). Voir le contrat détaillé dans
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
     *         supportée), ou si {@code date} n'est pas au format
     *         ISO-8601.
     */
    public List<Activity> findWithinBounds(
            String swLatitudeRaw, String swLongitudeRaw, String neLatitudeRaw, String neLongitudeRaw,
            String category, String dateRaw) {
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
        LocalDate date = parseOptionalDate(dateRaw);

        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinBounds(
                swLatitude, swLongitude, neLatitude, neLongitude, PUBLIC_STATUS, categoriesCsv, date);
    }

    /**
     * Consultation administrative par statut (LL-6005) : liste les
     * activités correspondant exactement au statut demandé, sans filtre
     * géographique — sert à consulter la file de modération (ex.
     * {@code status=PENDING}), à l'inverse de {@link #findNearby}/
     * {@link #findWithinBounds} qui, depuis LL-6004, ne retournent
     * jamais que {@code PUBLISHED}. Réservée aux administrateurs :
     * l'accès est contrôlé au niveau de
     * {@code SecurityConfig}/{@code AdminActivityController}
     * (rôle {@code ADMIN}, voir {@code JwtAuthentication}), pas ici —
     * cette méthode ne fait aucune vérification d'autorisation
     * elle-même, cohérent avec {@code createActivity} ci-dessous
     * (protégé de la même façon, au niveau {@code SecurityConfig}).
     *
     * {@code status} volontairement obligatoire (pas de valeur par
     * défaut) : contrairement à une recherche publique, il n'existe pas
     * de statut "par défaut" évident pour une consultation de
     * modération — lister toutes les activités sans distinction
     * reviendrait à réintroduire {@link #findAll}, déjà disponible.
     *
     * @throws IllegalArgumentException si {@code status} est manquant/
     *         vide ou ne correspond à aucune des trois valeurs
     *         formalisées en LL-6003 ({@code PENDING}/{@code
     *         PUBLISHED}/{@code REJECTED}).
     */
    public List<Activity> findByStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Le paramètre 'status' est obligatoire.");
        }
        if (!KNOWN_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Le paramètre 'status' ne correspond à aucune valeur connue.");
        }
        return activityRepository.findByStatus(status);
    }

    /**
     * Publie une activité (LL-6006) : transition {@code PENDING →
     * PUBLISHED}, voir {@link #transitionStatus} pour le détail commun
     * aux deux transitions (publier/rejeter).
     *
     * @return {@link Optional#empty()} si aucune activité ne correspond à
     *         {@code id} (le contrôleur traduit en {@code 404}) ; sinon
     *         l'activité mise à jour.
     * @throws IllegalArgumentException si l'activité existe mais n'est
     *         pas actuellement {@code PENDING} — voir {@link #transitionStatus}.
     */
    public Optional<Activity> publish(Long id) {
        return transitionStatus(id, "PUBLISHED");
    }

    /**
     * Rejette une activité (LL-6006) : transition {@code PENDING →
     * REJECTED}, voir {@link #transitionStatus} pour le détail commun
     * aux deux transitions (publier/rejeter).
     *
     * @return {@link Optional#empty()} si aucune activité ne correspond à
     *         {@code id} (le contrôleur traduit en {@code 404}) ; sinon
     *         l'activité mise à jour.
     * @throws IllegalArgumentException si l'activité existe mais n'est
     *         pas actuellement {@code PENDING} — voir {@link #transitionStatus}.
     */
    public Optional<Activity> reject(Long id) {
        return transitionStatus(id, "REJECTED");
    }

    /**
     * Logique commune à {@link #publish}/{@link #reject} (LL-6006, critère
     * d'acceptation « endpoints protégés / activité existante uniquement /
     * statut correctement modifié »). Charge l'activité, vérifie qu'elle
     * est bien {@code PENDING} (seul point de départ prévu par les
     * transitions documentées en LL-6003 sur {@link Activity#status()}),
     * puis sauvegarde une copie avec le nouveau statut — même pattern
     * « charger, copier avec le champ modifié, {@code save} » que
     * {@code ImportService#archiveMissingActivities} (LL-5009, transition
     * vers {@code ARCHIVED}) : {@code save} avec un {@code id} déjà
     * renseigné effectue une mise à jour, pas une insertion, comportement
     * déjà exploité ailleurs dans le projet.
     *
     * Absence d'id (activité inexistante) : renvoie {@link Optional#empty()}
     * plutôt que de lever une exception, même choix que
     * {@link #findById}/{@code ActivityController#getActivityById} —
     * garde la distinction 404 (ressource absente) / 400 (état invalide)
     * nette pour le contrôleur.
     *
     * ⚠️ Décision à valider avec Alex (point ouvert signalé dans
     * {@code NEXT_TASK.md}) : que faire si l'activité existe mais n'est
     * pas {@code PENDING} (déjà {@code PUBLISHED}/{@code REJECTED}) ?
     * Choix retenu ici : lever {@link IllegalArgumentException},
     * traduite en {@code 400} par le contrôleur — même convention que
     * partout ailleurs dans ce service pour une erreur de validation
     * métier (pas un no-op silencieux, pour ne pas laisser croire à
     * l'appelant qu'une transition a eu lieu ; pas de nouveau statut
     * HTTP introduit). Aucune machine à états ajoutée : une seule
     * vérification directe (statut actuel == {@code PENDING}), conforme
     * à l'interdiction explicite de {@code SPRINT_6.md} (« pas de
     * workflow de modération complexe »).
     */
    private Optional<Activity> transitionStatus(Long id, String newStatus) {
        Optional<Activity> existing = activityRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Activity activity = existing.get();
        if (!PENDING_STATUS.equals(activity.status())) {
            throw new IllegalArgumentException(
                    "L'activité " + id + " n'est pas en attente de modération (statut actuel : "
                            + activity.status() + "), aucune transition possible depuis ce statut.");
        }
        Activity updated = withStatus(activity, newStatus);
        return Optional.of(activityRepository.save(updated));
    }

    private Activity withStatus(Activity activity, String status) {
        return new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                status, activity.sourceId(), activity.importKey(), activity.url());
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
     * sont. Statut par défaut : {@code PENDING} (en attente de modération —
     * l'une des trois valeurs formalisées en LL-6003, voir
     * {@link Activity#status()}). Aucune date de
     * début/fin n'est demandée par le formulaire de contribution ; la date
     * de soumission est utilisée comme {@code startDate} en attendant un
     * futur ticket sur ce point. {@code url} toujours {@code null} : le
     * formulaire de contribution ne demande pas d'URL (LL-6002, hors
     * périmètre — voir {@code DATA_QUALITY_AUDIT.md}).
     *
     * Rattachée à la source réservée {@code MANUAL} (LL-5008, voir
     * {@code SOURCE_CONTRACT.md}) : critère d'acceptation explicite de
     * LL-5008, « création manuelle d'une activité non affectée » — le
     * comportement observable ne change pas, seul un {@code sourceId}
     * désormais obligatoire est renseigné en interne. {@code importKey}
     * reste {@code null} : aucune donnée collectée à déduplicer pour une
     * contribution manuelle.
     *
     * Validation ajoutée en LL-6002 (audit LL-6001, problème n°1 —
     * {@code title} n'était jusqu'ici pas du tout validé sur ce chemin) :
     * {@code title} obligatoire, non vide après {@code trim()}, longueur
     * ≤ 255 caractères (alignée sur la colonne {@code activity.title}) ;
     * {@code category}, si renseignée, non vide/blanche après
     * {@code trim()} (même interprétation minimale que
     * {@code NormalizationService}, {@code category} restant un champ
     * libre — voir sa javadoc) ; coordonnées revalidées après géocodage
     * par défense en profondeur, bien qu'improbable en pratique
     * (Nominatim ne renvoie que des coordonnées réelles).
     *
     * @throws IllegalArgumentException si {@code title} est manquant, vide
     *         ou trop long, ou si {@code category} est fournie mais
     *         vide/blanche.
     */
    public Activity createActivity(String title, String description, String category, String address) {
        validateTitle(title);
        validateCategory(category);
        Coordinates coordinates = geocodingService.geocode(address);
        validateLatitude("latitude", coordinates.latitude());
        validateLongitude("longitude", coordinates.longitude());
        Long manualSourceId = sourceService.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Source MANUAL introuvable — migration V8__create_source_table.sql manquante ?"));
        Activity activity = new Activity(
                null, title, description, category,
                coordinates.latitude(), coordinates.longitude(), LocalDateTime.now(), null, "PENDING",
                manualSourceId, null, null);
        return activityRepository.save(activity);
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le champ 'title' est obligatoire.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Le champ 'title' ne doit pas dépasser " + MAX_TITLE_LENGTH + " caractères.");
        }
    }

    private void validateCategory(String category) {
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("Le champ 'category', s'il est fourni, ne peut pas être vide.");
        }
    }

}
