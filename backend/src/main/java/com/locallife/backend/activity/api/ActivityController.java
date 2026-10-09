package com.locallife.backend.activity.api;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.premium.application.PremiumAccessPolicy;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import com.locallife.backend.tag.domain.ActivityTag;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la gestion des activités.
 * Consultation et création (contribution, LL-2012 ; adresse géocodée
 * côté serveur depuis LL-3012), recherche géographique par rayon
 * (LL-4003) et par zone rectangulaire (LL-4007).
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    /**
     * Valeur affichée quand une {@code Source} référencée par {@code sourceId} est introuvable
     * (LL-8006, cas défensif — ne devrait pas se produire, {@code sourceId} étant une FK).
     */
    private static final String UNKNOWN_SOURCE_NAME = "Source inconnue";

    /**
     * Type générique de lien (LL-EF-016), même convention que {@code
     * OpenAgendaCollector} pour un lien web ordinaire — {@code
     * ActivityLinkService} ne l'exige pas comme constante (validé comme
     * n'importe quelle URL, contrairement à {@code TYPE_PHONE}/{@code
     * TYPE_EMAIL}).
     */
    private static final String LINK_TYPE_WEBSITE = "WEBSITE";

    /** Seul statut lisible publiquement (LL-6004), voir {@link #isVisibleTo}. */
    private static final String PUBLISHED_STATUS = "PUBLISHED";

    private final ActivityService activityService;
    private final SourceService sourceService;
    private final UserService userService;
    private final LikeService likeService;
    private final ScheduleService scheduleService;
    private final OccurrenceService occurrenceService;
    private final LocationService locationService;
    private final MediaService mediaService;
    private final ActivityMediaService activityMediaService;
    private final ActivityLinkService activityLinkService;
    private final AccessibilityService accessibilityService;
    private final ActivityTagService activityTagService;
    private final ActivityMetadataService activityMetadataService;
    private final PremiumAccessPolicy premiumAccessPolicy;
    private final CategoryRepository categoryRepository;

    public ActivityController(
            ActivityService activityService, SourceService sourceService, UserService userService,
            LikeService likeService, ScheduleService scheduleService, OccurrenceService occurrenceService,
            LocationService locationService, MediaService mediaService, ActivityMediaService activityMediaService,
            ActivityLinkService activityLinkService, AccessibilityService accessibilityService,
            ActivityTagService activityTagService, ActivityMetadataService activityMetadataService,
            PremiumAccessPolicy premiumAccessPolicy, CategoryRepository categoryRepository) {
        this.activityService = activityService;
        this.sourceService = sourceService;
        this.userService = userService;
        this.likeService = likeService;
        this.scheduleService = scheduleService;
        this.occurrenceService = occurrenceService;
        this.locationService = locationService;
        this.mediaService = mediaService;
        this.activityMediaService = activityMediaService;
        this.activityLinkService = activityLinkService;
        this.accessibilityService = accessibilityService;
        this.activityTagService = activityTagService;
        this.activityMetadataService = activityMetadataService;
        this.premiumAccessPolicy = premiumAccessPolicy;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Liste des activités (LL-10006 : filtre optionnel par ville et tri
     * optionnel ; LL-11012 : filtre optionnel par date). Voir le contrat
     * détaillé dans {@code docs/02_Architecture/LOCATION_CONTRACT.md}
     * (section « Filtre `city` et tri `sort` »). Endpoint historiquement
     * sans paramètre : {@code city}/{@code sort}/{@code date} absents
     * préservent le comportement précédent (voir {@link
     * ActivityService#findAll}).
     *
     * <b>LL-11013</b> (« séparer les données nécessaires à la carte/liste
     * de celles nécessaires à la fiche détaillée ») : retourne désormais
     * {@link ActivitySummaryResponse} plutôt que {@link Activity}
     * directement — voir sa javadoc pour le détail des champs exposés et
     * la raison de ne pas réutiliser {@link ActivityResponse}.
     */
    @Operation(
            summary = "Liste les activités",
            description = "Retourne toutes les activités (représentation allégée, voir ActivitySummaryResponse), "
                    + "avec filtre optionnel par ville ('city', comparaison exacte insensible à la casse), "
                    + "filtre optionnel par date ('date', ISO-8601 yyyy-MM-dd) et tri optionnel ('sort', une ou "
                    + "plusieurs clés parmi 'city'/'date' séparées par une virgule, ex. 'city,date').")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Le paramètre 'sort' contient une valeur inconnue (autre que 'city'/'date') ou une "
                        + "clé en double, ou 'date' n'est pas au format ISO-8601 (yyyy-MM-dd).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Object> getAllActivities(
            @Parameter(description = "Filtre optionnel sur la ville (correspondance exacte, insensible à la "
                    + "casse). Absent → aucun filtrage. Ville ne correspondant à aucune activité → liste vide.")
            @RequestParam(required = false) String city,
            @Parameter(description = "Filtre optionnel sur une date (format ISO-8601 yyyy-MM-dd) : ne retient "
                    + "qu'une activité effectivement présente à cette date (LL-11012 — occurrence non annulée "
                    + "dont la période couvre cette date, ou période [startDate, endDate] pour une activité "
                    + "sans programmation détaillée). Absent → aucun filtrage (pas de fenêtre par défaut, "
                    + "contrairement à 'nearby'/'within-bounds').")
            @RequestParam(required = false) String date,
            @Parameter(description = "Tri optionnel : une ou plusieurs clés séparées par une virgule, parmi "
                    + "'city' et 'date' (ex. 'city', 'date', 'city,date'). Absent → ordre non garanti "
                    + "(comportement historique inchangé).")
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findAll(city, sort, date);
            List<ActivitySummaryResponse> summaries = activities.stream()
                    .map(activity -> ActivitySummaryResponse.from(activity, resolveMainImageUrl(activity.id())))
                    .toList();
            return ResponseEntity.ok(summaries);
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Recherche géographique (LL-4001/LL-4002/LL-4003/LL-4004/LL-4005) :
     * activités situées dans un rayon donné autour d'un point, triées par
     * distance croissante, avec filtres optionnels par catégorie et par
     * date ; ne retourne que les activités {@code PUBLISHED} depuis
     * LL-6004 (endpoint public, sans authentification — voir
     * {@link ActivityService#findNearby}). Voir le contrat détaillé dans
     * {@code docs/02_Architecture/GEO_SEARCH_CONTRACT.md}.
     *
     * Les paramètres sont reçus en {@code String} (et non {@code double}
     * avec {@code required = true}) volontairement : toute la validation
     * est faite dans {@link ActivityService#findNearby}, qui lève
     * {@link IllegalArgumentException} pour chaque cas d'erreur du contrat
     * (paramètre manquant, non numérique, hors plage),
     * attrapée ci-dessous et traduite en {@code 400}. Si on laissait Spring
     * MVC valider lui-même un {@code @RequestParam} obligatoire manquant,
     * l'exception résultante serait interceptée par
     * {@link com.locallife.backend.common.GlobalExceptionHandler} (qui
     * attrape {@code Exception} de façon générique) et renverrait {@code
     * 500} au lieu de {@code 400} — même choix de conception que {@code
     * createActivity} ci-dessous (LL-3012).
     */
    @Operation(
            summary = "Recherche des activités à proximité d'un point",
            description = "Retourne les activités PUBLISHED situées dans un rayon donné (en kilomètres, max 50) "
                    + "autour d'un point, triées par distance croissante. Distance calculée côté base "
                    + "(PostGIS ST_DWithin).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Paramètre manquant, non numérique, hors plage (latitude/longitude/radius), "
                        + "ou date au mauvais format.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nearby")
    public ResponseEntity<Object> getNearbyActivities(
            @Parameter(description = "Latitude du point de recherche, entre -90 et 90.", required = true)
            @RequestParam(required = false) String latitude,
            @Parameter(description = "Longitude du point de recherche, entre -180 et 180.", required = true)
            @RequestParam(required = false) String longitude,
            @Parameter(description = "Rayon de recherche en kilomètres, strictement positif, max 50.",
                    required = true)
            @RequestParam(required = false) String radius,
            @Parameter(description = "Filtre optionnel sur la/les catégorie(s), séparées par des virgules "
                    + "(ex. concert,marché). Catégorie inconnue → résultat vide, pas d'erreur.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filtre optionnel sur une date (format ISO-8601 yyyy-MM-dd). Une activité "
                    + "est retenue quand cette date tombe dans sa période [startDate, endDate]. Borne de début de "
                    + "la période quand 'dateTo' est également fourni.")
            @RequestParam(required = false) String date,
            @Parameter(description = "Borne de fin optionnelle de la période (format ISO-8601 yyyy-MM-dd), "
                    + "n'a de sens qu'accompagnée de 'date'. Quand les deux sont fournis, une activité est "
                    + "retenue dès que sa période [startDate, endDate] chevauche [date, dateTo].")
            @RequestParam(required = false) String dateTo,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findNearby(
                    latitude, longitude, radius, category, date, dateTo);
            return ResponseEntity.ok(withSourceNames(activities, authentication));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Recherche par zone cartographique (LL-4006/LL-4007) : activités
     * situées à l'intérieur du rectangle défini par les coins sud-ouest et
     * nord-est fournis, sans tri par distance (pas de point de référence
     * unique pour une zone rectangulaire), avec les mêmes filtres
     * optionnels par catégorie et date que {@code /nearby} (et la même
     * restriction au statut {@code PUBLISHED} depuis LL-6004). Voir
     * le contrat détaillé dans
     * {@code docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md}.
     *
     * Même choix de conception que {@link #getNearbyActivities} pour les
     * paramètres reçus en {@code String} : toute la validation est faite
     * dans {@link ActivityService#findWithinBounds}.
     */
    @Operation(
            summary = "Recherche des activités à l'intérieur d'une zone rectangulaire",
            description = "Retourne les activités PUBLISHED situées à l'intérieur du rectangle défini par les "
                    + "coins sud-ouest et nord-est fournis (typiquement la zone visible sur la carte). Résultats "
                    + "triés par id croissant (pas de tri par distance possible pour une zone).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Paramètre manquant, non numérique, hors plage (latitude/longitude), "
                        + "swLatitude/swLongitude non strictement inférieurs à neLatitude/neLongitude, "
                        + "ou date au mauvais format.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/within-bounds")
    public ResponseEntity<Object> getActivitiesWithinBounds(
            @Parameter(description = "Latitude du coin sud-ouest de la zone, entre -90 et 90.", required = true)
            @RequestParam(required = false) String swLatitude,
            @Parameter(description = "Longitude du coin sud-ouest de la zone, entre -180 et 180.", required = true)
            @RequestParam(required = false) String swLongitude,
            @Parameter(description = "Latitude du coin nord-est de la zone, entre -90 et 90.", required = true)
            @RequestParam(required = false) String neLatitude,
            @Parameter(description = "Longitude du coin nord-est de la zone, entre -180 et 180.", required = true)
            @RequestParam(required = false) String neLongitude,
            @Parameter(description = "Filtre optionnel sur la/les catégorie(s), séparées par des virgules "
                    + "(ex. concert,marché). Catégorie inconnue → résultat vide, pas d'erreur.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filtre optionnel sur une date (format ISO-8601 yyyy-MM-dd). Une activité "
                    + "est retenue quand cette date tombe dans sa période [startDate, endDate]. Borne de début de "
                    + "la période quand 'dateTo' est également fourni.")
            @RequestParam(required = false) String date,
            @Parameter(description = "Borne de fin optionnelle de la période (format ISO-8601 yyyy-MM-dd), "
                    + "n'a de sens qu'accompagnée de 'date'. Quand les deux sont fournis, une activité est "
                    + "retenue dès que sa période [startDate, endDate] chevauche [date, dateTo].")
            @RequestParam(required = false) String dateTo,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findWithinBounds(
                    swLatitude, swLongitude, neLatitude, neLongitude, category, date, dateTo);
            return ResponseEntity.ok(withSourceNames(activities, authentication));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Résout {@code sourceId} → nom de source pour une liste d'activités
     * (LL-8006), en une seule requête ({@link SourceService#getAllSources})
     * plutôt qu'un aller-retour par activité : le nombre de sources reste
     * faible (une poignée d'agendas OpenAgenda + la source {@code MANUAL},
     * voir {@code SOURCE_CONTRACT.md}), donc les charger toutes une fois
     * par appel est largement suffisant à l'échelle d'une bêta — pas de
     * cache introduit, hors périmètre de ce ticket de vérification.
     *
     * Résout également {@code createdBy} → nom d'utilisateur affiché
     * (LL-EF-010, {@code createdByUsername}) : contrairement aux sources,
     * il n'existe pas de {@code UserService#getAllUsers} (potentiellement
     * beaucoup d'utilisateurs, tout charger ne passerait pas à l'échelle
     * comme pour les sources) — un {@link UserService#getUserById} par id
     * <em>distinct</em> de {@code createdBy} rencontré dans la liste,
     * plutôt qu'un appel par activité : borné par le nombre de créateurs
     * différents parmi les résultats, pas par le nombre d'activités.
     *
     * Résout enfin {@code likedByCurrentUser} (LS-002) pour l'appelant
     * courant, quand authentifié : {@code /nearby}/{@code /within-bounds}
     * restent des endpoints publics (LL-4001, accessibles sans JWT), donc
     * {@code authentication} n'est pas forcément une {@link JwtAuthentication}
     * — l'authentification anonyme de Spring Security n'étant pas
     * désactivée dans {@code SecurityConfig}, un appelant non authentifié
     * fournit un {@code AnonymousAuthenticationToken}, jamais {@code null}
     * ; {@code instanceof} plutôt qu'un cast direct (contrairement à
     * {@code createActivity}/{@code cancelActivity}, où l'authentification
     * est <em>requise</em> par {@code SecurityConfig}, donc garantie
     * être une {@link JwtAuthentication}).
     */
    private List<ActivityResponse> withSourceNames(List<Activity> activities, Authentication authentication) {
        Map<Long, String> sourceNamesById = sourceService.getAllSources().stream()
                .collect(Collectors.toMap(Source::id, Source::name));
        Map<Long, String> usernamesByCreatedBy = activities.stream()
                .map(Activity::createdBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        createdBy -> createdBy,
                        createdBy -> userService.getUserById(createdBy).map(User::username).orElse(null)));

        List<Long> likedActivityIds = List.of();
        if (authentication instanceof JwtAuthentication jwtAuthentication) {
            List<Long> activityIds = activities.stream().map(Activity::id).collect(Collectors.toList());
            likedActivityIds = likeService.findLikedActivityIds(jwtAuthentication.getUserId(), activityIds);
        }
        Set<Long> likedActivityIdsSet = Set.copyOf(likedActivityIds);
        Map<Long, List<String>> categoryCodesByActivityId = resolveCategoryCodes(
                activities.stream().map(Activity::id).toList());

        return activities.stream()
                .map(activity -> ActivityResponse.from(
                        activity, sourceNamesById.getOrDefault(activity.sourceId(), UNKNOWN_SOURCE_NAME),
                        usernamesByCreatedBy.get(activity.createdBy()),
                        likedActivityIdsSet.contains(activity.id()),
                        resolveMainImageUrl(activity.id()),
                        categoryCodesByActivityId.getOrDefault(activity.id(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * Codes des catégories du référentiel par activité, en deux requêtes pour toute la liste
     * (liens {@code activity_category}, puis référentiel {@code category}, une dizaine de lignes).
     */
    private Map<Long, List<String>> resolveCategoryCodes(List<Long> activityIds) {
        Map<Long, List<Long>> categoryIdsByActivityId = activityService.findCategoryIds(activityIds);
        if (categoryIdsByActivityId == null || categoryIdsByActivityId.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> codesById = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::id, Category::code));
        Map<Long, List<String>> categoryCodesByActivityId = new LinkedHashMap<>();
        categoryIdsByActivityId.forEach((activityId, categoryIds) -> categoryCodesByActivityId.put(
                activityId, categoryIds.stream().map(codesById::get).filter(Objects::nonNull).toList()));
        return categoryCodesByActivityId;
    }

    /**
     * Une activité non {@code PUBLISHED} ({@code PENDING}, {@code REJECTED},
     * {@code ARCHIVED}, {@code CANCELLED}) n'est visible sur l'endpoint
     * public {@code GET /activities/{id}} que de son auteur et des
     * administrateurs — même règle de visibilité que les recherches
     * publiques (LL-6004), qui ne retournent que du {@code PUBLISHED}.
     */
    private boolean isVisibleTo(Activity activity, Authentication authentication) {
        if (PUBLISHED_STATUS.equals(activity.status())) {
            return true;
        }
        if (authentication instanceof JwtAuthentication jwtAuthentication) {
            return jwtAuthentication.getRole() == Role.ADMIN
                    || Objects.equals(jwtAuthentication.getUserId(), activity.createdBy());
        }
        return false;
    }

    /**
     * Fiche d'une activité (LL-11013 puis LL-11014). Retourne {@link
     * ActivityDetailResponse} (fiche détaillée) ou {@link
     * ActivitySummaryResponse} (résumé) selon que l'appelant a ou non
     * accès à la fiche détaillée, décidé exclusivement par {@link
     * #premiumAccessPolicy} — voir sa javadoc et {@code
     * docs/ADR-0008-preparation-premium.md} : aucune notion de Premium
     * ici, dans {@link ActivityService} ni dans le domaine, uniquement
     * à cette unique frontière API.
     *
     * Une activité non publiée répond 404 à tout autre appelant que son
     * auteur ou un administrateur (voir {@link #isVisibleTo}) : 404 plutôt
     * que 403, pour ne pas révéler son existence.
     *
     * {@code authentication} jamais {@code null} (endpoint public, voir
     * la javadoc de {@link #withSourceNames}) — transmis tel quel à
     * {@link PremiumAccessPolicy#hasPremiumAccess}, qui gère lui-même le
     * cas d'un appelant non connecté.
     */
    @Operation(
            summary = "Détail d'une activité",
            description = "Retourne la fiche d'une activité : la fiche détaillée (description, programmation, "
                    + "occurrences non annulées, lieux, médias, liens, accessibilité, tags, informations "
                    + "complémentaires — voir ActivityDetailResponse) si l'appelant y a accès, sinon un résumé "
                    + "(voir ActivitySummaryResponse).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activité trouvée."),
        @ApiResponse(responseCode = "404",
                description = "Aucune activité ne correspond à cet id, ou elle n'est pas publiée et "
                        + "l'appelant n'en est ni l'auteur ni un administrateur.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getActivityById(
            @Parameter(description = "Identifiant de l'activité.", required = true) @PathVariable Long id,
            Authentication authentication) {
        return activityService.findById(id)
                .filter(activity -> isVisibleTo(activity, authentication))
                .map(activity -> ResponseEntity.ok(premiumAccessPolicy.hasPremiumAccess(authentication)
                        ? toDetailResponse(activity)
                        : (Object) ActivitySummaryResponse.from(activity, resolveMainImageUrl(activity.id()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Url du premier média attaché à une activité (position 0, voir
     * {@code ActivityMediaService#findByActivityId}), ou {@code null} si
     * aucun média n'est attaché — utilisé par {@link #getAllActivities}
     * pour construire {@link ActivitySummaryResponse#mainImageUrl}.
     *
     * ⚠️ Une requête par activité (ni mise en cache ni résolution en
     * lot) : même limitation assumée que {@link #withSourceNames}/la
     * résolution des noms d'utilisateur ci-dessus (voir leur javadoc),
     * suffisant à l'échelle d'une bêta.
     */
    private String resolveMainImageUrl(Long activityId) {
        return activityMediaService.findByActivityId(activityId).stream()
                .findFirst()
                .map(ActivityMedia::mediaId)
                .flatMap(mediaService::findById)
                .map(Media::url)
                .orElse(null);
    }

    /**
     * Assemble la fiche détaillée d'une activité (LL-11013) à partir de
     * l'ensemble des services du modèle événementiel riche du Sprint 11.
     *
     * {@code locations} : dédoublonnées (un même lieu peut être
     * référencé par plusieurs schedules/occurrences, voir la javadoc de
     * {@code Location}) — collectées dans un {@link LinkedHashMap} (clé
     * {@code id}) plutôt qu'une simple {@link List}, pour ne jamais
     * renvoyer deux fois le même lieu dans la fiche détaillée, tout en
     * conservant l'ordre de première rencontre (schedules d'abord, puis
     * occurrences).
     *
     * {@code occurrences} : LL-11016 (« Cas 4 — Exception ») a révélé
     * que l'interprétation initiale de LL-11013 (« pertinentes » = non
     * annulées, {@code OccurrenceService#findEffectiveByScheduleId})
     * empêchait justement de représenter une exception — « 15/09 →
     * annulé » doit apparaître dans la fiche, pas en être absent.
     * Corrigé : toutes les occurrences d'un schedule ({@code
     * OccurrenceService#findByScheduleId}, {@code CANCELLED} incluses),
     * across tous les schedules de l'activité — {@code
     * OccurrenceSummary#status} (déjà exposé depuis LL-11013) permet au
     * frontend de distinguer une occurrence annulée d'une occurrence
     * normale (voir {@code docs/ADR-0010-cas-reels-programmation.md}).
     */
    private ActivityDetailResponse toDetailResponse(Activity activity) {
        List<Schedule> schedules = scheduleService.findByActivityId(activity.id());

        List<Occurrence> occurrences = new ArrayList<>();
        Map<Long, Location> locationsById = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            if (schedule.locationId() != null) {
                locationService.findById(schedule.locationId()).ifPresent(
                        location -> locationsById.put(location.id(), location));
            }
            List<Occurrence> scheduleOccurrences = occurrenceService.findByScheduleId(schedule.id());
            occurrences.addAll(scheduleOccurrences);
            for (Occurrence occurrence : scheduleOccurrences) {
                if (occurrence.locationId() != null) {
                    locationService.findById(occurrence.locationId()).ifPresent(
                            location -> locationsById.put(location.id(), location));
                }
            }
        }

        List<Media> media = activityMediaService.findByActivityId(activity.id()).stream()
                .map(ActivityMedia::mediaId)
                .map(mediaService::findById)
                .flatMap(Optional::stream)
                .toList();

        List<ActivityLink> links = activityLinkService.findByActivityId(activity.id());
        Accessibility accessibility = accessibilityService.findByActivityId(activity.id()).orElse(null);
        List<String> tags = activityTagService.findByActivityId(activity.id()).stream()
                .map(ActivityTag::tag)
                .toList();
        String additionalInfo = activityMetadataService.findByActivityId(activity.id())
                .map(metadata -> metadata.data().value())
                .orElse(null);

        List<String> categoryCodes = resolveCategoryCodes(List.of(activity.id()))
                .getOrDefault(activity.id(), List.of());

        return ActivityDetailResponse.from(
                activity, schedules, occurrences, List.copyOf(locationsById.values()), media, links,
                accessibility, tags, additionalInfo, categoryCodes);
    }

    @PostMapping
    public ResponseEntity<Object> createActivity(
            @RequestBody CreateActivityRequest request, Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // LL-EF-016 : validés avant même la création de l'activité (voir la javadoc de
            // CreateActivityRequest et ActivityLinkService#validateBeforeCreate).
            if (hasText(request.url())) {
                activityLinkService.validateBeforeCreate(LINK_TYPE_WEBSITE, request.url());
            }
            if (hasText(request.phone())) {
                activityLinkService.validateBeforeCreate(ActivityLinkService.TYPE_PHONE, request.phone());
            }
            if (hasText(request.email())) {
                activityLinkService.validateBeforeCreate(ActivityLinkService.TYPE_EMAIL, request.email());
            }

            // Issue #45 : une activité a 1..N catégories (contrainte en base, V42). Pour une
            // contribution manuelle, ce sont celles choisies par le contributeur (categoryCodes,
            // ou l'ancien categoryCode unique), pas déduites automatiquement (CAT-008 ne concerne
            // que les imports).
            List<Long> categoryIds = resolveRequestedCategoryIds(request);

            // authentication non-null garanti : POST /api/v1/activities exige un JWT valide
            // (voir SecurityConfig), JwtFilter ne laisse passer que des JwtAuthentication.
            Long createdBy = ((JwtAuthentication) authentication).getUserId();
            Activity activity = activityService.createActivity(
                    request.title(), request.description(), request.category(), request.address(),
                    request.startDate(), request.endDate(), createdBy, categoryIds);

            attachContactLinks(activity.id(), request);

            return ResponseEntity.status(HttpStatus.CREATED).body(activity);
        } catch (IllegalArgumentException | AddressNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Catégories choisies par le contributeur : {@code categoryCodes} s'il contient au moins un
     * code non blanc, sinon l'ancien {@code categoryCode} unique. Doublons ignorés, ordre conservé.
     *
     * @throws IllegalArgumentException si aucun code n'est fourni ou si l'un d'eux est inconnu
     */
    private List<Long> resolveRequestedCategoryIds(CreateActivityRequest request) {
        List<String> codes = request.categoryCodes() == null ? List.of() : request.categoryCodes().stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            codes = Collections.singletonList(request.categoryCode());
        }
        List<Long> categoryIds = new ArrayList<>();
        for (String code : codes) {
            categoryIds.add(categoryRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Catégorie inconnue : " + code))
                    .id());
        }
        return categoryIds;
    }

    /**
     * Attache les liens/contacts optionnels d'une contribution manuelle
     * (LL-EF-016) — appelée après {@link ActivityService#createActivity},
     * une fois l'activité déjà persistée et son id connu ; ne revalide
     * pas {@code url}/{@code phone}/{@code email} (déjà fait par {@link
     * #createActivity} avant la création de l'activité, voir sa
     * javadoc).
     */
    private void attachContactLinks(Long activityId, CreateActivityRequest request) {
        if (hasText(request.url())) {
            activityLinkService.create(new ActivityLink(null, activityId, LINK_TYPE_WEBSITE, null, request.url()));
        }
        if (hasText(request.phone())) {
            activityLinkService.create(
                    new ActivityLink(null, activityId, ActivityLinkService.TYPE_PHONE, null, request.phone()));
        }
        if (hasText(request.email())) {
            activityLinkService.create(
                    new ActivityLink(null, activityId, ActivityLinkService.TYPE_EMAIL, null, request.email()));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Annule une activité créée manuellement (LL-EF-010), réservé à son
     * créateur ou à un administrateur — voir {@link ActivityService#cancel}
     * pour le détail des règles (statuts annulables, activités importées
     * exclues). Endpoint authentifié (voir {@code SecurityConfig},
     * {@code .requestMatchers(HttpMethod.PATCH,
     * "/api/v1/activities/*&#47;cancel").authenticated()}) mais sans
     * restriction de rôle statique : contrairement à {@code
     * AdminActivityController#publishActivity}/{@code #rejectActivity}
     * (réservés {@code ADMIN} au niveau {@code SecurityConfig}),
     * l'autorisation ici dépend de la donnée (le créateur de <em>cette</em>
     * activité) et est donc vérifiée dans le service, pas ici — ce
     * contrôleur se contente de transmettre l'identité du demandeur
     * extraite du JWT, même pattern que {@link #createActivity} ci-dessus.
     */
    @Operation(
            summary = "Annule une activité créée manuellement",
            description = "Fait passer une activité PENDING ou PUBLISHED au statut CANCELLED. Réservé au "
                    + "créateur de l'activité ou à un administrateur. Sans effet sur les activités importées "
                    + "(non concernées par cette fonctionnalité).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activité annulée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "L'activité n'a pas de créateur identifié, ou n'est pas dans un statut annulable.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403",
                description = "Le demandeur n'est ni le créateur de l'activité ni un administrateur.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Aucune activité ne correspond à cet id.")
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Object> cancelActivity(
            @Parameter(description = "Identifiant de l'activité à annuler.", required = true)
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        JwtAuthentication jwtAuthentication = (JwtAuthentication) authentication;
        boolean isAdmin = jwtAuthentication.getRole() == Role.ADMIN;
        try {
            return activityService.cancel(id, jwtAuthentication.getUserId(), isAdmin)
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (ActivityAccessDeniedException exception) {
            return errorResponse(HttpStatus.FORBIDDEN, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Like une activité pour le compte de l'utilisateur connecté
     * (LS-002, critère « un utilisateur connecté peut liker un
     * événement »). Endpoint authentifié (voir {@code SecurityConfig})
     * — répond au critère « un utilisateur non connecté ne peut pas
     * enregistrer de favoris » en amont, avant même d'atteindre ce
     * contrôleur.
     *
     * Idempotent (voir {@code LikeService#like}) : cliquer plusieurs fois
     * sur « J'aime » ne produit jamais d'erreur, contrairement à
     * {@code cancelActivity} ci-dessus où une transition de statut
     * invalide en est une — un like n'a pas d'états multiples à
     * distinguer, seulement présent/absent.
     */
    @Operation(
            summary = "Like une activité",
            description = "Ajoute l'activité aux favoris de l'utilisateur connecté. Idempotent : sans effet si "
                    + "déjà likée.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activité likée (ou déjà likée)."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "404", description = "Aucune activité ne correspond à cet id.")
    })
    @PostMapping("/{id}/like")
    public ResponseEntity<Object> likeActivity(
            @Parameter(description = "Identifiant de l'activité à liker.", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        boolean liked = likeService.like(userId, id);
        if (!liked) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new LikeStatusResponse(true));
    }

    /**
     * Retire le like de l'utilisateur connecté sur une activité
     * (LS-002, critère « l'utilisateur peut retirer son like »).
     * Idempotent, même raisonnement que {@link #likeActivity} — voir
     * {@code LikeService#unlike}. Ne renvoie jamais {@code 404} même si
     * l'activité n'existe pas/plus : une activité inexistante ne peut de
     * toute façon avoir aucun like actif à retirer (voir la javadoc de
     * {@code LikeService#unlike}), le résultat pour l'appelant est le
     * même dans les deux cas — « n'est plus liké ».
     */
    @Operation(
            summary = "Retire le like d'une activité",
            description = "Retire l'activité des favoris de l'utilisateur connecté. Idempotent : sans effet si "
                    + "pas (ou plus) likée.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Like retiré (ou déjà absent)."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide).")
    })
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Object> unlikeActivity(
            @Parameter(description = "Identifiant de l'activité à déliker.", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        likeService.unlike(userId, id);
        return ResponseEntity.ok(new LikeStatusResponse(false));
    }

    /**
     * Réponse pour {@link #likeActivity}/{@link #unlikeActivity}
     * (LS-002) : confirme explicitement le nouvel état plutôt qu'un
     * simple {@code 200} vide, pour que le frontend puisse mettre à jour
     * le bouton « J'aime » sans dépendre uniquement du code HTTP.
     */
    public record LikeStatusResponse(boolean liked) {
    }

    /**
     * Corps de requête pour la contribution d'une activité (LL-2012).
     * Depuis LL-3012, le client envoie une {@code address} (texte libre) au
     * lieu de latitude/longitude : le backend géocode l'adresse côté serveur
     * et ne conserve que les coordonnées obtenues, pas l'adresse elle-même.
     * {@code id} et {@code status} restent gérés côté serveur.
     *
     * {@code startDate}/{@code endDate} (demande Alex) : chaînes ISO-8601
     * optionnelles ({@code yyyy-MM-dd} ou {@code yyyy-MM-ddTHH:mm}),
     * {@code null}/absentes acceptées — voir {@code ActivityService
     * #createActivity} pour les valeurs par défaut appliquées dans ce cas
     * ({@code startDate} → maintenant, {@code endDate} → début de journée
     * de {@code startDate}).
     */
    /**
     * {@code url}/{@code phone}/{@code email} ajoutés en LL-EF-016 :
     * chacun optionnel (peut être {@code null} ou absent du JSON), et
     * validé — voir {@link ActivityLinkService} — avant même que
     * l'activité ne soit créée (voir {@link #createActivity}). Devient,
     * si renseigné, un {@link ActivityLink} attaché à l'activité créée
     * (types {@code WEBSITE}/{@code PHONE}/{@code EMAIL}) — même modèle
     * que celui déjà exposé en lecture par {@link ActivityDetailResponse}
     * (LL-11008/LL-11013), pas une structure dédiée : une activité peut
     * déjà porter plusieurs liens/contacts, sans changement nécessaire
     * pour ce ticket (critère d'acceptation « structure suffisamment
     * propre pour permettre plusieurs contacts ultérieurement »).
     */
    /**
     * {@code categoryCode} ajouté en CAT-006 (correctif) : code d'une des
     * 9 catégories V1 (CAT-001, ex. {@code CULTURE}), choisi explicitement
     * par le contributeur — jamais déduit automatiquement (CAT-008 n'est
     * pas encore implémenté et ne concerne de toute façon que les
     * imports, pas la saisie manuelle). Résolu en {@code Category} par
     * {@link #createActivity}, qui rejette (400) un code manquant ou
     * inconnu.
     *
     * {@code categoryCodes} : plusieurs catégories pour une même contribution (modèle 1..N,
     * issue #45). Prioritaire sur {@code categoryCode}, conservé pour les clients qui n'envoient
     * qu'une catégorie.
     */
    public record CreateActivityRequest(
            String title, String description, String category, String address,
            String startDate, String endDate, String url, String phone, String email,
            String categoryCode, List<String> categoryCodes) {

        /** Une seule catégorie ({@code categoryCode}), sans {@code categoryCodes}. */
        public CreateActivityRequest(
                String title, String description, String category, String address,
                String startDate, String endDate, String url, String phone, String email,
                String categoryCode) {
            this(title, description, category, address, startDate, endDate, url, phone, email, categoryCode,
                    null);
        }
    }

}
