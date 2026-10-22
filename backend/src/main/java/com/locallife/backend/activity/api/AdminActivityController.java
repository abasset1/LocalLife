package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityBulkDeletionService;
import com.locallife.backend.activity.application.ActivityBulkDeletionService.Criteria;
import com.locallife.backend.activity.application.ActivityBulkDeletionService.DeletionCountChangedException;
import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.application.AdminActivityService;
import com.locallife.backend.activity.application.AdminActivityService.BulkResult;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.auth.application.ReauthenticationService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation et la modération administrative
 * des activités (LL-6005 : consultation par statut ; LL-6006 : publier/
 * rejeter, Sprint 6). Contrôleur distinct d'{@link ActivityController}
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
    private final ActivityBulkDeletionService bulkDeletionService;
    private final AdminActivityService adminActivityService;
    private final ReauthenticationService reauthenticationService;

    public AdminActivityController(ActivityService activityService, ActivityBulkDeletionService bulkDeletionService,
            AdminActivityService adminActivityService, ReauthenticationService reauthenticationService) {
        this.adminActivityService = adminActivityService;
        this.reauthenticationService = reauthenticationService;
        this.activityService = activityService;
        this.bulkDeletionService = bulkDeletionService;
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

    /**
     * Publie une activité (LL-6006) : transition {@code PENDING →
     * PUBLISHED}, voir {@link ActivityService#publish} pour le détail
     * (activité inexistante → {@code 404}, activité pas {@code PENDING}
     * → {@code 400}). Même protection que {@link #getActivitiesByStatus}
     * (rôle {@code ADMIN}, voir {@code SecurityConfig}), pas vérifiée ici.
     */
    @Operation(
            summary = "Publie une activité en attente de modération (réservé aux administrateurs)",
            description = "Fait passer une activité PENDING au statut PUBLISHED. Réservé au rôle ADMIN ; 403 pour sa "
                    + "propre activité.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activité publiée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "L'activité existe mais n'est pas actuellement PENDING.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune activité ne correspond à cet id.")
    })
    @PatchMapping("/{id}/publish")
    public ResponseEntity<Object> publishActivity(
            @Parameter(description = "Identifiant de l'activité à publier.", required = true)
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return applyTransition(() -> adminActivityService.publish(adminId(authentication), id), httpRequest);
    }

    /**
     * Rejette une activité (LL-6006) : transition {@code PENDING →
     * REJECTED}, voir {@link ActivityService#reject} pour le détail.
     * Même remarques que {@link #publishActivity} ci-dessus.
     */
    @Operation(
            summary = "Rejette une activité en attente de modération (réservé aux administrateurs)",
            description = "Fait passer une activité PENDING au statut REJECTED. Corps {reason} obligatoire : le "
                    + "motif est envoyé à l'auteur et visible dans ses contributions. 403 pour sa propre activité.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activité rejetée avec succès."),
        @ApiResponse(responseCode = "400",
                description = "L'activité existe mais n'est pas actuellement PENDING.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune activité ne correspond à cet id.")
    })
    @PatchMapping("/{id}/reject")
    public ResponseEntity<Object> rejectActivity(
            @Parameter(description = "Identifiant de l'activité à rejeter.", required = true)
            @PathVariable Long id,
            @RequestBody(required = false) ReasonRequest body,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return applyTransition(() -> adminActivityService.reject(adminId(authentication), id,
                body == null ? null : body.reason()), httpRequest);
    }

    @Operation(
            summary = "Compte les activités qu'une suppression viserait (réservé aux administrateurs)",
            description = "Aperçu avant suppression : mêmes critères que DELETE /api/v1/admin/activities.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nombre d'activités visées."),
        @ApiResponse(responseCode = "400", description = "Aucun critère (sans all=true), ou critère invalide.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @GetMapping("/deletion-preview")
    public ResponseEntity<Object> previewDeletion(
            @Parameter(description = "Source des activités.") @RequestParam(required = false) Long sourceId,
            @Parameter(description = "Statut des activités.") @RequestParam(required = false) String status,
            @Parameter(description = "Début au plus tôt ce jour (inclus).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @Parameter(description = "Début au plus tard ce jour (inclus).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @Parameter(description = "Vrai pour viser toutes les activités, sans autre critère.")
            @RequestParam(defaultValue = "false") boolean all,
            HttpServletRequest httpRequest) {
        try {
            Criteria criteria = new Criteria(sourceId, blankToNull(status), startFrom, startTo, all);
            long count = bulkDeletionService.count(criteria);
            return ResponseEntity.ok(new DeletionCountResponse(count));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    @Operation(
            summary = "Met des activités à la corbeille (réservé aux administrateurs)",
            description = "Toutes les activités qui répondent aux critères (source, statut, période de début), ou "
                    + "toutes avec all=true, passent à la corbeille : invisibles partout, restaurables 30 jours "
                    + "(d'un coup avec l'identifiant de lot renvoyé), puis purgées. expectedCount doit reprendre le "
                    + "nombre de l'aperçu (409 sinon) ; motif obligatoire ; mot de passe de l'administrateur dans "
                    + "l'en-tête X-Confirm-Password (428 sinon). Les auteurs des contributions visées sont prévenus.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activités mises à la corbeille ; nombre et lot."),
        @ApiResponse(responseCode = "400", description = "Aucun critère (sans all=true), critère invalide ou motif "
                + "manquant.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "409", description = "Le nombre d'activités visées a changé depuis l'aperçu.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "428", description = "Mot de passe à confirmer.")
    })
    @DeleteMapping
    public ResponseEntity<Object> deleteActivities(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(defaultValue = "false") boolean all,
            @Parameter(description = "Nombre d'activités annoncé par l'aperçu.", required = true)
            @RequestParam long expectedCount,
            @Parameter(description = "Motif, inscrit au journal et envoyé aux auteurs.") @RequestParam(required = false)
            String reason,
            @RequestHeader(value = ReauthenticationService.HEADER, required = false) String password,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            reauthenticationService.require(adminId(authentication), password);
            BulkResult result = adminActivityService.trashMany(adminId(authentication),
                    new Criteria(sourceId, blankToNull(status), startFrom, startTo, all), expectedCount, reason);
            return ResponseEntity.ok(new TrashResultResponse(result.count(), result.batch()));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (DeletionCountChangedException exception) {
            return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), httpRequest);
        }
    }

    @Operation(
            summary = "Met une activité à la corbeille (réservé aux administrateurs)",
            description = "Contribution ou activité importée ; motif obligatoire (paramètre reason), envoyé à "
                    + "l'auteur d'une contribution. Restaurable 30 jours.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Activité mise à la corbeille."),
        @ApiResponse(responseCode = "400", description = "Motif manquant."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune activité ne correspond à cet id, ou déjà à la "
                + "corbeille.")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteActivity(@PathVariable long id,
            @RequestParam(required = false) String reason, Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            return adminActivityService.trashOne(adminId(authentication), id, reason)
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    @Operation(summary = "Corbeille", description = "Activités supprimées par un administrateur, les plus récentes "
            + "d'abord ; purgées 30 jours après leur suppression.")
    @GetMapping("/trash")
    public ResponseEntity<Object> trash(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, HttpServletRequest httpRequest) {
        if (page < 0 || size < 1 || size > 200) {
            return errorResponse(HttpStatus.BAD_REQUEST, "'page' positif, 'size' entre 1 et 200.", httpRequest);
        }
        return ResponseEntity.ok(adminActivityService.trash(page, size));
    }

    @Operation(summary = "Restaurer une activité de la corbeille", description = "Elle reprend son statut d'avant.")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Object> restore(@PathVariable long id, Authentication authentication) {
        return adminActivityService.restore(adminId(authentication), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Restaurer tout un lot de suppression", description = "Toutes les activités encore à la "
            + "corbeille d'une même suppression en masse.")
    @PostMapping("/trash/batches/{batch}/restore")
    public ResponseEntity<Object> restoreBatch(@PathVariable UUID batch, Authentication authentication) {
        return ResponseEntity.ok(new DeletionCountResponse(adminActivityService.restoreBatch(adminId(authentication),
                batch)));
    }

    /** Motif d'un refus. */
    public record ReasonRequest(String reason) {
    }

    /** Résultat d'une mise à la corbeille en masse. */
    public record TrashResultResponse(long count, UUID batch) {
    }

    private static Long adminId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Nombre d'activités visées (aperçu) ou supprimées. */
    public record DeletionCountResponse(long count) {
    }

    /**
     * Factorise le traitement commun à {@link #publishActivity}/
     * {@link #rejectActivity} : {@link Optional#empty()} → {@code 404}
     * (sans corps, même convention que
     * {@code ActivityController#getActivityById}) ; présence → {@code 200}
     * avec l'activité mise à jour ; {@link IllegalArgumentException}
     * (transition invalide, voir {@link ActivityService#publish}) →
     * {@code 400}, même convention que {@link #getActivitiesByStatus}
     * ci-dessus.
     */
    private ResponseEntity<Object> applyTransition(
            Supplier<Optional<Activity>> transition, HttpServletRequest httpRequest) {
        try {
            return transition.get()
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

}
