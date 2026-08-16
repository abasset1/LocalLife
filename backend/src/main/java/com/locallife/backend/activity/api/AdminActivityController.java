package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation administrative des activités
 * (LL-6005, Sprint 6). Contrôleur distinct d'{@link ActivityController}
 * plutôt qu'une méthode supplémentaire dessus : chemin d'accès différent
 * ({@code /api/v1/admin/activities} contre {@code /api/v1/activities}),
 * protection différente (rôle {@code ADMIN} requis, voir
 * {@code SecurityConfig}), et objectif différent (file de modération,
 * pas recherche publique) — les regrouper aurait mélangé deux
 * responsabilités dans la même classe.
 *
 * Protection effective au niveau {@code SecurityConfig}
 * ({@code .requestMatchers(HttpMethod.GET, "/api/v1/admin/activities")
 * .hasRole("ADMIN")}), pas ici : ce contrôleur ne vérifie aucune
 * autorisation lui-même, cohérent avec {@code POST /api/v1/activities}/
 * {@code POST /api/v1/users} (LL-3008), protégés de la même façon.
 */
@RestController
@RequestMapping("/api/v1/admin/activities")
public class AdminActivityController {

    private final ActivityService activityService;

    public AdminActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * Liste les activités correspondant exactement au statut demandé
     * (ex. {@code PENDING} pour la file de modération). Voir
     * {@link ActivityService#findByStatus} pour le détail de la
     * validation (paramètre obligatoire, doit être une des trois
     * valeurs formalisées en LL-6003).
     */
    @Operation(
            summary = "Liste les activités par statut (réservé aux administrateurs)",
            description = "Retourne les activités correspondant exactement au statut demandé (PENDING, "
                    + "PUBLISHED ou REJECTED), sans filtre géographique. Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consultation effectuée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "Paramètre 'status' manquant ou ne correspondant à aucune valeur connue.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @GetMapping
    public ResponseEntity<Object> getActivitiesByStatus(
            @Parameter(description = "Statut recherché : PENDING, PUBLISHED ou REJECTED.", required = true)
            @RequestParam(required = false) String status,
            HttpServletRequest httpRequest) {
        try {
            List<Activity> activities = activityService.findByStatus(status);
            return ResponseEntity.ok(activities);
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

}
