package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * côté serveur depuis LL-3012), recherche géographique (LL-4003).
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<List<Activity>> getAllActivities() {
        List<Activity> activities = activityService.findAll();
        return ResponseEntity.ok(activities);
    }

    /**
     * Recherche géographique (LL-4001/LL-4002/LL-4003/LL-4004/LL-4005) :
     * activités situées dans un rayon donné autour d'un point, triées par
     * distance croissante, avec filtres optionnels par statut, par
     * catégorie et par date. Voir le contrat détaillé dans
     * {@code docs/02_Architecture/GEO_SEARCH_CONTRACT.md}.
     *
     * Les paramètres sont reçus en {@code String} (et non {@code double}
     * avec {@code required = true}) volontairement : toute la validation
     * est faite dans {@link ActivityService#findNearby}, qui lève
     * {@link IllegalArgumentException} pour chaque cas d'erreur du contrat
     * (paramètre manquant, non numérique, hors plage, statut inconnu),
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
            description = "Retourne les activités situées dans un rayon donné (en kilomètres, max 50) "
                    + "autour d'un point, triées par distance croissante. Distance calculée côté base "
                    + "(PostGIS ST_DWithin).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Paramètre manquant, non numérique, hors plage (latitude/longitude/radius), "
                        + "statut inconnu, ou date au mauvais format.",
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
            @Parameter(description = "Filtre optionnel sur le statut de l'activité (ex. PUBLISHED, PENDING).")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtre optionnel sur la/les catégorie(s), séparées par des virgules "
                    + "(ex. concert,marché). Catégorie inconnue → résultat vide, pas d'erreur.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filtre optionnel sur une date (format ISO-8601 yyyy-MM-dd). Une activité "
                    + "est retenue quand cette date tombe dans sa période [startDate, endDate].")
            @RequestParam(required = false) String date,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities =
                    activityService.findNearby(latitude, longitude, radius, status, category, date);
            return ResponseEntity.ok(activities);
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
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
                    request.title(), request.description(), request.category(), request.address());
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
     * id, dates et statut sont gérés côté serveur.
     */
    public record CreateActivityRequest(String title, String description, String category, String address) {
    }

}
