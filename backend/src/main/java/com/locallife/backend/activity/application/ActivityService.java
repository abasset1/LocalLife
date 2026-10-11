package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * consultation administrative par statut). {@code ARCHIVED} ajouté en
     * LL-7007 (V13, ré-import faisant disparaître une activité de sa
     * source) manquait initialement ici — corrigé au passage.
     * {@code CANCELLED} ajouté en LL-EF-010 (voir {@link #cancel}).
     */
    private static final Set<String> KNOWN_STATUSES =
            Set.of("PENDING", "PUBLISHED", "REJECTED", "ARCHIVED", "CANCELLED");

    /**
     * Clés de tri connues du paramètre {@code sort} (LL-10006), voir
     * {@link #findAll(String, String)} et le contrat détaillé dans
     * {@code docs/02_Architecture/LOCATION_CONTRACT.md} (section « Filtre
     * `city` et tri `sort` »).
     */
    private static final Set<String> KNOWN_SORT_KEYS = Set.of("city", "date");

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
    private final ActivityClassificationService activityClassificationService;

    public ActivityService(
            ActivityRepository activityRepository, GeocodingService geocodingService, SourceService sourceService,
            ActivityClassificationService activityClassificationService) {
        this.activityRepository = activityRepository;
        this.geocodingService = geocodingService;
        this.sourceService = sourceService;
        this.activityClassificationService = activityClassificationService;
    }

    public List<Activity> findAll() {
        return activityRepository.findAll();
    }

    /**
     * Liste des activités avec filtres optionnels par ville et par date, et
     * tri optionnel (LL-10006, filtre {@code date} ajouté en LL-11012),
     * pour {@code GET /api/v1/activities}. Voir le contrat détaillé dans
     * {@code docs/02_Architecture/LOCATION_CONTRACT.md} (section « Filtre
     * `city` et tri `sort` ») et {@code docs/ADR-0006-recherche-occurrences.md}
     * pour {@code date}.
     *
     * Activités {@code PUBLISHED} uniquement (endpoint public) : une
     * activité en attente de validation, refusée, annulée ou archivée n'est
     * jamais listée, avec ou sans filtre. Avant ce correctif, la liste
     * renvoyait tous les statuts (constat de l'audit #93).
     *
     * Ni {@code city}, ni {@code sort}, ni {@code date} fournis : simple
     * lecture par statut, sans passer par {@link
     * ActivityRepository#findAllFiltered}.
     *
     * {@code city} : comparé tel quel (après nettoyage des espaces en
     * début/fin), la comparaison insensible à la casse est faite côté SQL
     * (voir {@link ActivityRepository#findAllFiltered}). Une valeur
     * vide/blanche équivaut à une absence de filtre (même convention que
     * {@code category} sur {@link #findNearby}).
     *
     * {@code date} (LL-11012) : date unique au format ISO-8601 ({@code
     * yyyy-MM-dd}), même format/validation que {@link #findNearby} (voir
     * {@link #parseOptionalDate}) — mais sans {@code dateTo} ni fenêtre par
     * défaut (voir {@link ActivityRepository#findAllFiltered}, qui reste
     * une simple liste sans restriction implicite en l'absence de filtre).
     *
     * {@code sort} : liste de clés séparées par des virgules, parmi
     * exactement {@code "city"}/{@code "date"} (voir {@link #parseSort}).
     * L'ordre des clés fixe la priorité du tri ; au plus deux clés ont un
     * sens (il n'existe que deux clés connues) — une troisième occurrence
     * serait nécessairement un doublon, rejeté par {@link #parseSort}.
     *
     * @throws IllegalArgumentException si {@code sort} contient une valeur
     *         inconnue (autre que {@code city}/{@code date}) ou une clé en
     *         double, ou si {@code date} n'est pas au format ISO-8601.
     */
    public List<Activity> findAll(String city, String sortRaw, String dateRaw) {
        String normalizedCity = normalizeCity(city);
        List<String> sortKeys = parseSort(sortRaw);
        LocalDate date = parseOptionalDate("date", dateRaw);

        if (normalizedCity == null && sortKeys.isEmpty() && date == null) {
            return activityRepository.findByStatus(PUBLIC_STATUS);
        }

        String primarySort = sortKeys.size() > 0 ? sortKeys.get(0) : null;
        String secondarySort = sortKeys.size() > 1 ? sortKeys.get(1) : null;
        return activityRepository.findAllFiltered(normalizedCity, date, primarySort, secondarySort);
    }

    private String normalizeCity(String cityRaw) {
        if (cityRaw == null || cityRaw.isBlank()) {
            return null;
        }
        return cityRaw.trim();
    }

    /**
     * Analyse et valide le paramètre {@code sort} (LL-10006) : liste de
     * clés séparées par des virgules, dont chaque valeur doit appartenir à
     * {@link #KNOWN_SORT_KEYS}, sans doublon. {@code null}/vide/blanc →
     * liste vide (pas de tri demandé), même convention que
     * {@link #normalizeCategories} pour {@code category}.
     *
     * @throws IllegalArgumentException si une clé ne fait pas partie de
     *         {@link #KNOWN_SORT_KEYS}, ou si une clé apparaît plusieurs
     *         fois.
     */
    private List<String> parseSort(String sortRaw) {
        if (sortRaw == null || sortRaw.isBlank()) {
            return List.of();
        }
        List<String> keys = Arrays.stream(sortRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
        for (String key : keys) {
            if (!KNOWN_SORT_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "Le paramètre 'sort' ne peut contenir que 'city' et/ou 'date' (valeur reçue : '"
                                + key + "').");
            }
        }
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("Le paramètre 'sort' ne doit pas contenir de clé en double.");
        }
        return keys;
    }

    public Optional<Activity> findById(Long id) {
        return activityRepository.findById(id);
    }

    /**
     * Catégories du référentiel de plusieurs activités (id d'activité → ids de catégories), pour que
     * les réponses de recherche exposent les catégories répertoriées plutôt que la seule chaîne
     * libre {@code category}. Délégué à {@code ActivityClassificationService}.
     */
    public Map<Long, List<Long>> findCategoryIds(Collection<Long> activityIds) {
        return activityClassificationService.getCategoryIdsByActivityId(activityIds);
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
     * virgules (ex. {@code "concert,marché"} ou {@code "CULTURE,SPORT"}),
     * ou {@code null} pour ne pas filtrer. Chaque valeur correspond soit à
     * la chaîne libre décrite ci-dessous, soit à un code du référentiel
     * ({@code category.code}, catégories de l'activité dans
     * {@code activity_category}) — voir {@code ActivityRepository}. Chaque
     * valeur est nettoyée (espaces retirés, valeurs vides ignorées). La
     * chaîne libre est comparée telle quelle à la colonne {@code category}
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
     * ({@code yyyy-MM-dd}). Une activité est retenue quand cette date
     * tombe dans sa période {@code [startDate, endDate]} (bornes
     * incluses, comparaison au jour près — l'heure de {@code
     * startDate}/{@code endDate} n'entre pas en jeu). Couvre à la fois
     * les activités d'une seule journée et celles s'étalant sur
     * plusieurs jours. {@code endDate} peut être absent en base (import
     * OpenAgenda sans date de fin renseignée sur l'événement source —
     * depuis l'ajout des dates à la saisie manuelle, {@code
     * createActivity} ci-dessous ne laisse elle-même plus jamais {@code
     * endDate} à {@code null}, voir sa javadoc) : dans ce cas l'activité
     * est traitée comme ne durant que la journée de {@code startDate},
     * voir {@link ActivityRepository#findWithinRadius} pour le détail SQL.
     *
     * Si {@code date} n'est pas fourni, les recherches publiques retournent
     * toutes les activités encore en cours ou à venir : leur fin (ou leur
     * début lorsque {@code endDate} est absent) doit être aujourd'hui ou
     * dans le futur. Ce comportement est appliqué par le repository afin
     * de conserver une seule définition du jour courant pour les recherches
     * géographiques. Pour consulter une activité passée, il faut fournir
     * {@code date} explicitement.
     * Ce comportement par défaut est volontairement limité aux recherches
     * publiques ({@link #findNearby}/{@link #findWithinBounds}) :
     * {@link #findByStatus} (consultation administrative, LL-6005) n'est
     * pas concernée.
     *
     * {@code dateTo} (LL-11003) : borne de fin optionnelle de la période,
     * au même format que {@code date} — n'a de sens qu'accompagnée de
     * {@code date} (borne de début), voir {@link #validateDateRange}.
     * Quand les deux sont fournies, une activité est retenue dès lors que
     * sa période {@code [startDate, endDate]} chevauche {@code [date,
     * dateTo]} (bornes incluses des deux côtés, comparaison au jour près) —
     * et non plus seulement lorsque sa période couvre exactement
     * {@code date}. Sert notamment aux filtres « cette semaine »/« ce
     * mois-ci » du frontend (LL-11002/LL-11003) : sans {@code dateTo}, une
     * activité commençant un jour de la période mais visible seulement en
     * fin de période (ou inversement) n'était pas retournée. Absence de
     * {@code dateTo} → comportement inchangé (correspondance sur une seule
     * date), pour rester rétrocompatible avec les appelants existants.
     *
     * @throws IllegalArgumentException si un paramètre obligatoire est
     *         manquant/non numérique, hors des contraintes du contrat
     *         LL-4001 (latitude/longitude hors plage, rayon ≤ 0 ou
     *         &gt; 50 km), si {@code date}/{@code dateTo} n'est pas au
     *         format ISO-8601 ({@code yyyy-MM-dd}), si {@code dateTo} est
     *         fournie sans {@code date}, ou si {@code dateTo} est
     *         antérieure à {@code date}.
     */
    public List<Activity> findNearby(
            String latitudeRaw, String longitudeRaw, String radiusRaw, String category, String dateRaw,
            String dateToRaw) {
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
        LocalDate date = parseOptionalDate("date", dateRaw);
        LocalDate dateTo = parseOptionalDate("dateTo", dateToRaw);
        validateDateRange(date, dateTo);

        double radiusMeters = radiusKm * 1000;
        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinRadius(
                latitude, longitude, radiusMeters, PUBLIC_STATUS, categoriesCsv, date, dateTo);
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
     * {@code dateTo} (LL-11003) : même sémantique que sur {@link
     * #findNearby} (borne de fin optionnelle de la période, n'a de sens
     * qu'accompagnée de {@code date}) — voir sa javadoc pour le détail.
     *
     * @throws IllegalArgumentException si un paramètre obligatoire est
     *         manquant/non numérique, si une latitude/longitude est hors
     *         plage (-90/90, -180/180), si {@code swLatitude >=
     *         neLatitude} ou {@code swLongitude >= neLongitude} (contrat
     *         LL-4006 : la traversée de l'antiméridien n'est pas
     *         supportée), si {@code date}/{@code dateTo} n'est pas au
     *         format ISO-8601, si {@code dateTo} est fournie sans
     *         {@code date}, ou si {@code dateTo} est antérieure à
     *         {@code date}.
     */
    public List<Activity> findWithinBounds(
            String swLatitudeRaw, String swLongitudeRaw, String neLatitudeRaw, String neLongitudeRaw,
            String category, String dateRaw, String dateToRaw) {
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
        LocalDate date = parseOptionalDate("date", dateRaw);
        LocalDate dateTo = parseOptionalDate("dateTo", dateToRaw);
        validateDateRange(date, dateTo);

        String categoriesCsv = normalizeCategories(category);
        return activityRepository.findWithinBounds(
                swLatitude, swLongitude, neLatitude, neLongitude, PUBLIC_STATUS, categoriesCsv, date, dateTo);
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
                status, activity.sourceId(), activity.importKey(), activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy());
    }

    /**
     * Statuts depuis lesquels une activité manuelle peut être annulée
     * (LL-EF-010) : {@code PENDING} (encore en attente de modération) ou
     * {@code PUBLISHED} (déjà visible publiquement — cas principal visé
     * par le ticket, annuler un événement qui n'aura finalement pas lieu).
     * {@code REJECTED}/{@code CANCELLED} volontairement exclus : une
     * activité déjà rejetée ou déjà annulée n'a pas de raison d'être
     * annulée à nouveau — voir {@link #cancel} pour le message d'erreur
     * associé.
     */
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(PENDING_STATUS, "PUBLISHED");

    /**
     * Annule une activité créée manuellement (LL-EF-010) : transition
     * {@code PENDING → CANCELLED} ou {@code PUBLISHED → CANCELLED} (voir
     * {@link #CANCELLABLE_STATUSES}), réservée à son créateur ou à un
     * administrateur (critère d'acceptation explicite du ticket, section
     * « Droits »). Contrairement à {@link #publish}/{@link #reject}
     * (réservés au rôle {@code ADMIN}, vérifié au niveau
     * {@code SecurityConfig}), l'autorisation ici dépend des <em>données</em>
     * (le créateur de <em>cette</em> activité précisément) et ne peut donc
     * pas être exprimée par une règle statique {@code SecurityConfig} —
     * elle est vérifiée ici, dans le service, à partir de l'identité du
     * demandeur transmise par le contrôleur (extraite du JWT, voir
     * {@code ActivityController#cancelActivity}).
     *
     * {@code requestingUserIsAdmin} plutôt qu'un import du type
     * {@code Role} du module {@code user} : le domaine/service
     * {@code activity} ne doit pas dépendre d'un autre module autrement
     * que par référence d'id (voir {@code ARCHITECTURE.md}, déjà le cas
     * pour {@code Activity#sourceId}/{@code createdBy}) — un simple
     * booléen, décidé par l'appelant à partir de
     * {@code JwtAuthentication#getRole()}, suffit ici et évite cette
     * dépendance.
     *
     * Vérifications, dans l'ordre :
     * <ol>
     *   <li>l'activité existe ({@link Optional#empty()} sinon, → {@code 404}
     *       côté contrôleur, même convention que {@link #transitionStatus}) ;</li>
     *   <li>l'activité a un créateur identifié ({@code createdBy != null}) —
     *       sinon elle provient d'un import (hors périmètre du ticket) ou
     *       est une contribution manuelle antérieure à LL-EF-010 (créateur
     *       non identifiable a posteriori, voir
     *       {@code V26__add_created_by_to_activity.sql}) : dans les deux
     *       cas, aucune annulation n'est possible faute de créateur à
     *       autoriser — {@link IllegalArgumentException} (→ {@code 400}) ;</li>
     *   <li>le demandeur est autorisé (créateur de l'activité, ou
     *       administrateur) — sinon {@link ActivityAccessDeniedException}
     *       (→ {@code 403} côté contrôleur), distincte d'{@link
     *       IllegalArgumentException} : il ne s'agit pas d'une donnée
     *       invalide mais d'un refus d'autorisation, une distinction déjà
     *       établie par {@code SecurityConfig} pour les règles statiques
     *       (401/403) que ce cas-ci ne peut pas exprimer de la même
     *       façon (voir plus haut) ;</li>
     *   <li>l'activité est dans un statut annulable ({@link
     *       #CANCELLABLE_STATUSES}) — sinon {@link IllegalArgumentException},
     *       même convention que {@link #transitionStatus} pour une
     *       transition invalide.</li>
     * </ol>
     *
     * @return {@link Optional#empty()} si aucune activité ne correspond à
     *         {@code id} ; sinon l'activité mise à jour.
     * @throws IllegalArgumentException si l'activité n'a pas de créateur
     *         identifié, ou si elle n'est pas actuellement dans un statut
     *         annulable ({@code PENDING}/{@code PUBLISHED}).
     * @throws ActivityAccessDeniedException si le demandeur n'est ni le
     *         créateur de l'activité ni un administrateur.
     */
    public Optional<Activity> cancel(Long id, Long requestingUserId, boolean requestingUserIsAdmin) {
        Optional<Activity> existing = activityRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Activity activity = existing.get();
        if (activity.createdBy() == null) {
            throw new IllegalArgumentException(
                    "L'activité " + id + " n'a pas de créateur identifié (activité importée, ou contribution "
                            + "manuelle antérieure à l'introduction de cette fonctionnalité) : impossible de "
                            + "l'annuler.");
        }
        if (!requestingUserIsAdmin && !activity.createdBy().equals(requestingUserId)) {
            throw new ActivityAccessDeniedException(
                    "Seul le créateur de l'activité " + id + " ou un administrateur peut l'annuler.");
        }
        if (!CANCELLABLE_STATUSES.contains(activity.status())) {
            throw new IllegalArgumentException(
                    "L'activité " + id + " n'est pas dans un statut annulable (statut actuel : "
                            + activity.status() + "), aucune annulation possible depuis ce statut.");
        }
        Activity updated = withStatus(activity, "CANCELLED");
        return Optional.of(activityRepository.save(updated));
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

    private LocalDate parseOptionalDate(String paramName, String dateRaw) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateRaw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Le paramètre '" + paramName + "' doit être au format ISO-8601 (yyyy-MM-dd).");
        }
    }

    /**
     * Valide la cohérence de la paire {@code date}/{@code dateTo} (LL-11003 :
     * filtre par période, plutôt qu'une unique date, pour les recherches
     * publiques — voir {@link #findNearby}/{@link #findWithinBounds}).
     * {@code dateTo} n'a de sens qu'accompagné de {@code date} (borne de
     * début de la période) : le fournir seul serait ambigu (période allant
     * de quand à {@code dateTo} ?), donc rejeté explicitement plutôt que
     * silencieusement ignoré. Quand les deux sont fournis, {@code dateTo}
     * doit être postérieure ou égale à {@code date} — une période inversée
     * ne correspond à aucun cas d'usage et masquerait probablement une
     * erreur côté appelant.
     */
    private void validateDateRange(LocalDate date, LocalDate dateTo) {
        if (dateTo != null && date == null) {
            throw new IllegalArgumentException(
                    "Le paramètre 'dateTo' ne peut être fourni sans 'date' (borne de début de la période).");
        }
        if (date != null && dateTo != null && dateTo.isBefore(date)) {
            throw new IllegalArgumentException(
                    "Le paramètre 'dateTo' doit être postérieure ou égale à 'date'.");
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
     * géocodée depuis LL-3012). Depuis LL-EF-008, l'adresse saisie par le
     * contributeur est désormais conservée telle quelle (colonne
     * {@code address}) — elle a déjà servi de source de vérité pour le
     * géocodage, il n'y a donc aucune raison de la perdre. {@code city}
     * (utilisée pour le regroupement de la vue liste, voir le frontend) et
     * {@code postalCode} proviennent de la même réponse Nominatim que les
     * coordonnées ({@link GeocodingService}, paramètre
     * {@code addressdetails=1}) : aucun appel réseau supplémentaire
     * n'est nécessaire. Statut par défaut : {@code PENDING} (en attente de modération —
     * l'une des trois valeurs formalisées en LL-6003, voir
     * {@link Activity#status()}). {@code url} toujours {@code null} : le
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
     * <b>{@code startDate}/{@code endDate}</b> (LL-2012 initialement ne
     * demandait aucune date de fin/début explicite — la date de
     * soumission servait de {@code startDate}, {@code endDate} restait
     * {@code null} ; ajoutés comme paramètres du formulaire à la demande
     * d'Alex, chacun optionnel (chaîne vide ou {@code null} acceptée)) :
     * <ul>
     *   <li>{@code startDate} absent → valeur par défaut {@link
     *       LocalDateTime#now()} (« sysdate »), comportement historique
     *       inchangé pour un appelant qui ne fournit rien ;</li>
     *   <li>{@code endDate} absent → valeur par défaut le début de la
     *       journée de {@code startDate} (« trunc(date de début) », au
     *       sens SQL du terme : même jour, heure remise à minuit) — pas
     *       {@code null} comme avant cette évolution, décision explicite
     *       d'Alex. ⚠️ Cas particulier à noter : si {@code startDate}
     *       est <em>lui aussi</em> absent (donc égal à « maintenant »,
     *       heure comprise), {@code endDate} par défaut (minuit ce
     *       jour-là) tombe alors chronologiquement <strong>avant</strong>
     *       {@code startDate} — id est, la formule de troncature
     *       s'applique à l'heure près, dans son sens SQL littéral, sans
     *       garde-fou {@code endDate ≥ startDate} ajouté ici (non demandé,
     *       aurait empêché ce cas par défaut précisément).</li>
     * </ul>
     * Les deux paramètres acceptent soit une date seule ({@code
     * yyyy-MM-dd}, minuit implicite — cas attendu d'un simple sélecteur
     * de date côté formulaire), soit une date-heure ISO-8601 complète
     * ({@code yyyy-MM-ddTHH:mm[:ss]}), voir {@link #parseFlexibleDate}.
     * Une valeur fournie mais illisible lève {@link
     * IllegalArgumentException} (→ {@code 400}, même convention que
     * {@link #validateTitle}), plutôt que d'être silencieusement ignorée.
     *
     * <b>{@code createdBy}</b> ajouté en LL-EF-010 : id de l'utilisateur
     * authentifié à l'origine de la contribution (extrait du JWT par
     * {@code ActivityController#createActivity}, cet endpoint exigeant
     * déjà une authentification — voir {@code SecurityConfig} — mais sans
     * jusqu'ici exploiter l'identité de l'appelant). Permet d'afficher le
     * créateur et de l'autoriser à annuler son activité, voir
     * {@link #cancel}. Paramètre simple plutôt que réextrait ici depuis un
     * {@code Authentication} : ce service ne dépend pas de Spring
     * Security, cohérent avec le reste de la classe (voir {@link #findByStatus}).
     *
     * @throws IllegalArgumentException si {@code title} est manquant, vide
     *         ou trop long, si {@code category} est fournie mais
     *         vide/blanche, si {@code startDate}/{@code endDate} est
     *         fournie mais illisible, ou si {@code categoryIds} est
     *         {@code null} ou vide (issue #45 : une activité a 1..N
     *         catégories, contrainte réelle en base depuis
     *         {@code V42__activity_category_n_categories.sql} — ce
     *         contrôle n'est qu'un échec rapide côté application, la
     *         contrainte base reste la garantie réelle). La résolution
     *         d'un {@code categoryCode} fourni par l'appelant vers un id
     *         est à la charge de {@code ActivityController#createActivity},
     *         pas de cette méthode (qui ne connaît pas le référentiel
     *         {@code Category}, pour ne pas lui ajouter une dépendance).
     */
    public Activity createActivity(
            String title, String description, String category, String address,
            String startDate, String endDate, Long createdBy, List<Long> categoryIds) {
        validateTitle(title);
        validateCategory(category);
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Au moins une catégorie est obligatoire.");
        }
        LocalDateTime resolvedStartDate = parseFlexibleDate("startDate", startDate)
                .orElseGet(LocalDateTime::now);
        LocalDateTime resolvedEndDate = parseFlexibleDate("endDate", endDate)
                .orElseGet(() -> resolvedStartDate.toLocalDate().atStartOfDay());
        Coordinates coordinates = geocodingService.geocode(address);
        validateLatitude("latitude", coordinates.latitude());
        validateLongitude("longitude", coordinates.longitude());
        Long manualSourceId = sourceService.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Source MANUAL introuvable — migration V8__create_source_table.sql manquante ?"));
        Activity activity = new Activity(
                null, title, description, category,
                coordinates.latitude(), coordinates.longitude(), resolvedStartDate, resolvedEndDate, "PENDING",
                manualSourceId, null, null,
                address, coordinates.city(), coordinates.postalCode(), null, null, null, null, createdBy);
        return activityClassificationService.createActivity(activity, categoryIds);
    }

    /**
     * Modifie une contribution manuelle : mêmes règles que {@link #createActivity} (titre, catégories,
     * dates par défaut), statut fixé par l'appelant. L'adresse n'est géocodée de nouveau que si elle
     * change ; sinon coordonnées, ville et code postal sont conservés. Remplace les catégories.
     *
     * @throws IllegalArgumentException si un champ est invalide
     */
    @Transactional
    public Activity updateContribution(
            Activity existing, String title, String description, String category, String address,
            String startDate, String endDate, String status, List<Long> categoryIds) {
        validateTitle(title);
        validateCategory(category);
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Au moins une catégorie est obligatoire.");
        }
        LocalDateTime resolvedStartDate = parseFlexibleDate("startDate", startDate)
                .orElseGet(LocalDateTime::now);
        LocalDateTime resolvedEndDate = parseFlexibleDate("endDate", endDate)
                .orElseGet(() -> resolvedStartDate.toLocalDate().atStartOfDay());
        boolean sameAddress = address != null && existing.address() != null
                && address.trim().equalsIgnoreCase(existing.address().trim());
        double latitude = existing.latitude();
        double longitude = existing.longitude();
        String city = existing.city();
        String postalCode = existing.postalCode();
        if (!sameAddress) {
            Coordinates coordinates = geocodingService.geocode(address);
            validateLatitude("latitude", coordinates.latitude());
            validateLongitude("longitude", coordinates.longitude());
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
            city = coordinates.city();
            postalCode = coordinates.postalCode();
        }
        Activity updated = activityRepository.save(new Activity(
                existing.id(), title, description, category, latitude, longitude, resolvedStartDate,
                resolvedEndDate, status, existing.sourceId(), existing.importKey(), existing.url(),
                sameAddress ? existing.address() : address, city, postalCode, existing.longDescription(),
                existing.conditions(), existing.ageMin(), existing.ageMax(), existing.createdBy()));
        activityClassificationService.replaceCategories(updated.id(), categoryIds);
        return updated;
    }

    /**
     * Voir la javadoc de {@link #createActivity} pour le contrat exact.
     * {@code null}/vide → {@link Optional#empty()} (valeur par défaut à
     * appliquer par l'appelant), pas d'exception — distinct d'une valeur
     * fournie mais illisible, qui lève {@link IllegalArgumentException}.
     */
    private Optional<LocalDateTime> parseFlexibleDate(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException firstAttempt) {
            try {
                return Optional.of(LocalDate.parse(value).atStartOfDay());
            } catch (DateTimeParseException secondAttempt) {
                throw new IllegalArgumentException(
                        "Le champ '" + fieldName + "' doit être une date (yyyy-MM-dd) ou une date-heure "
                                + "ISO-8601 (yyyy-MM-ddTHH:mm) valide.");
            }
        }
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
