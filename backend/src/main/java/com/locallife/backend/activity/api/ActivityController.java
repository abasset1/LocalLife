package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
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

    private final ActivityService activityService;
    private final SourceService sourceService;
    private final UserService userService;
    private final LikeService likeService;

    public ActivityController(
            ActivityService activityService, SourceService sourceService, UserService userService,
            LikeService likeService) {
        this.activityService = activityService;
        this.sourceService = sourceService;
        this.userService = userService;
        this.likeService = likeService;
    }

    /**
     * Liste des activités (LL-10006 : filtre optionnel par ville et tri
     * optionnel). Voir le contrat détaillé dans
     * {@code docs/02_Architecture/LOCATION_CONTRACT.md} (section « Filtre
     * `city` et tri `sort` »). Endpoint historiquement sans paramètre :
     * {@code city}/{@code sort} absents préservent le comportement
     * précédent (voir {@link ActivityService#findAll(String, String)}).
     */
    @Operation(
            summary = "Liste les activités",
            description = "Retourne toutes les activités, avec filtre optionnel par ville ('city', comparaison "
                    + "exacte insensible à la casse), filtre optionnel par date ('date', ISO-8601 yyyy-MM-dd) et "
                    + "tri optionnel ('sort', une ou plusieurs clés parmi 'city'/'date' séparées par une virgule, "
                    + "ex. 'city,date').")
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
            return ResponseEntity.ok(activities);
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

        return activities.stream()
                .map(activity -> ActivityResponse.from(
                        activity, sourceNamesById.getOrDefault(activity.sourceId(), UNKNOWN_SOURCE_NAME),
                        usernamesByCreatedBy.get(activity.createdBy()),
                        likedActivityIdsSet.contains(activity.id())))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Activity> getActivityById(@PathVariable Long id) {
        Optional<Activity> activity = activityService.findById(id);
        return activity.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    public ResponseEntity<Object> createActivity(
            @RequestBody CreateActivityRequest request, Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // authentication non-null garanti : POST /api/v1/activities exige un JWT valide
            // (voir SecurityConfig), JwtFilter ne laisse passer que des JwtAuthentication.
            Long createdBy = ((JwtAuthentication) authentication).getUserId();
            Activity activity = activityService.createActivity(
                    request.title(), request.description(), request.category(), request.address(),
                    request.startDate(), request.endDate(), createdBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(activity);
        } catch (IllegalArgumentException | AddressNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
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
    public record CreateActivityRequest(
            String title, String description, String category, String address,
            String startDate, String endDate) {
    }

}
