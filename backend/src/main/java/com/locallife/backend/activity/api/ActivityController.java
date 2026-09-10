package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
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
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public ActivityController(ActivityService activityService, SourceService sourceService) {
        this.activityService = activityService;
        this.sourceService = sourceService;
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
                    + "exacte insensible à la casse) et tri optionnel ('sort', une ou plusieurs clés parmi "
                    + "'city'/'date' séparées par une virgule, ex. 'city,date').")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Le paramètre 'sort' contient une valeur inconnue (autre que 'city'/'date') ou une "
                        + "clé en double.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Object> getAllActivities(
            @Parameter(description = "Filtre optionnel sur la ville (correspondance exacte, insensible à la "
                    + "casse). Absent → aucun filtrage. Ville ne correspondant à aucune activité → liste vide.")
            @RequestParam(required = false) String city,
            @Parameter(description = "Tri optionnel : une ou plusieurs clés séparées par une virgule, parmi "
                    + "'city' et 'date' (ex. 'city', 'date', 'city,date'). Absent → ordre non garanti "
                    + "(comportement historique inchangé).")
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findAll(city, sort);
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
                    + "est retenue quand cette date tombe dans sa période [startDate, endDate].")
            @RequestParam(required = false) String date,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findNearby(latitude, longitude, radius, category, date);
            return ResponseEntity.ok(withSourceNames(activities));
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
                    + "est retenue quand cette date tombe dans sa période [startDate, endDate].")
            @RequestParam(required = false) String date,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findWithinBounds(
                    swLatitude, swLongitude, neLatitude, neLongitude, category, date);
            return ResponseEntity.ok(withSourceNames(activities));
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
     */
    private List<ActivityResponse> withSourceNames(List<Activity> activities) {
        Map<Long, String> sourceNamesById = sourceService.getAllSources().stream()
                .collect(Collectors.toMap(Source::id, Source::name));
        return activities.stream()
                .map(activity -> ActivityResponse.from(
                        activity, sourceNamesById.getOrDefault(activity.sourceId(), UNKNOWN_SOURCE_NAME)))
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
            @RequestBody CreateActivityRequest request, HttpServletRequest httpRequest) {
        try {
            Activity activity = activityService.createActivity(
                    request.title(), request.description(), request.category(), request.address(),
                    request.startDate(), request.endDate());
            return ResponseEntity.status(HttpStatus.CREATED).body(activity);
        } catch (IllegalArgumentException | AddressNotFoundException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (GeocodingUnavailableException exception) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
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
