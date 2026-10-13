package com.locallife.backend.collector.api;

import com.locallife.backend.collector.application.AgendaDiscoveryService;
import com.locallife.backend.collector.application.ImportAlreadyRunningException;
import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import com.locallife.backend.collector.application.SourceActivationService;
import com.locallife.backend.collector.application.SourceDryRunReport;
import com.locallife.backend.collector.application.SourceDryRunService;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Déclencheur contrôlé du pipeline d'import (LL-7002, Sprint 7).
 *
 * <b>Décision MVP</b> (voir {@code SPRINT_7.md}) : déclenchement manuel,
 * protégé par le rôle {@code ADMIN} — pas de scheduler, conformément à la
 * règle du sprint « aucun scheduler complexe ». Contrôleur distinct
 * plutôt qu'une méthode ajoutée à {@code AdminActivityController} : chemin
 * différent ({@code /api/v1/admin/import}), et objectif différent
 * (déclencher le pipeline de collecte, pas gérer des activités déjà
 * persistées) — même raisonnement que celui documenté dans la javadoc
 * d'{@code AdminActivityController} pour justifier sa séparation
 * d'{@code ActivityController}.
 *
 * Protection effective au niveau {@code SecurityConfig}
 * ({@code .requestMatchers(HttpMethod.POST, "/api/v1/admin/import")
 * .hasRole("ADMIN")}), pas ici : même convention que tous les autres
 * endpoints d'administration du projet (LL-6005/LL-6006/LL-3008).
 *
 * <b>Réutilisation du pipeline existant sans duplication</b> (critère
 * d'acceptation du ticket) : ce contrôleur ne fait qu'invoquer
 * {@link ImportService#importAll()}, déjà responsable de l'orchestration
 * complète {@code Collector → Normalisation → Validation → Persistance}
 * depuis LL-5008 — aucune logique d'import dupliquée ici.
 *
 * <b>Journalisation</b> (critère d'acceptation du ticket) : déjà assurée
 * par {@link ImportService} lui-même (LL-5009, un log {@code INFO} par
 * source traitée) — ce contrôleur ne journalise rien de plus, pour ne pas
 * dupliquer une responsabilité déjà couverte. Le résultat détaillé
 * ({@link ImportResult}, un par {@code Collector} enregistré) est en plus
 * renvoyé dans la réponse HTTP, pour un retour immédiat à l'administrateur
 * sans avoir à consulter les logs.
 *
 * Un échec de collecte (ex. configuration OpenAgenda manquante, panne
 * réseau) ne fait pas échouer cet appel : {@link ImportService} le
 * capture déjà et le traduit en un {@code ImportResult} dégradé
 * ({@code errors = 1}), voir sa javadoc — cet endpoint répond donc
 * toujours {@code 200} avec le détail du résultat, y compris en cas
 * d'échec de collecte, plutôt que de renvoyer une erreur HTTP générique
 * qui masquerait ce détail.
 */
@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminImportController {

    private final ImportService importService;
    private final AgendaDiscoveryService agendaDiscoveryService;
    private final SourceDryRunService sourceDryRunService;
    private final SourceActivationService sourceActivationService;

    public AdminImportController(
            ImportService importService,
            AgendaDiscoveryService agendaDiscoveryService,
            SourceDryRunService sourceDryRunService,
            SourceActivationService sourceActivationService) {
        this.importService = importService;
        this.agendaDiscoveryService = agendaDiscoveryService;
        this.sourceDryRunService = sourceDryRunService;
        this.sourceActivationService = sourceActivationService;
    }

    @Operation(
            summary = "Essai à blanc d'une source (réservé aux administrateurs)",
            description = "Collecte la source et calcule ce qu'un import ferait (créations, mises à jour, "
                    + "archivage), sans aucune écriture. Verdict OK, FAILED (configuration invalide ou collecte "
                    + "en échec) ou SUSPICIOUS (résultat à vérifier).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Essai effectué ; voir le verdict dans la réponse."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune source ne correspond à cet id.")
    })
    @PostMapping("/sources/{id}/dry-run")
    public ResponseEntity<SourceDryRunReport> dryRun(
            @Parameter(description = "Identifiant de la source.", required = true) @PathVariable Long id) {
        return sourceDryRunService.dryRun(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Active une source après un essai à blanc réussi (réservé aux administrateurs)",
            description = "Lance un essai à blanc ; la source n'est activée que si son verdict est OK. Seul "
                    + "moyen d'activer une source DATATOURISME.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Source activée ; bilan de l'essai dans la réponse."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune source ne correspond à cet id."),
        @ApiResponse(responseCode = "409",
                description = "Activation refusée (verdict FAILED ou SUSPICIOUS) ; bilan dans la réponse.")
    })
    @PostMapping("/sources/{id}/activate")
    public ResponseEntity<SourceDryRunReport> activate(
            @Parameter(description = "Identifiant de la source.", required = true) @PathVariable Long id) {
        return sourceActivationService.activate(id)
                .map(report -> report.activated()
                        ? ResponseEntity.ok(report)
                        : ResponseEntity.status(HttpStatus.CONFLICT).body(report))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Déclenche un import réel (réservé aux administrateurs)",
            description = "Exécute le pipeline Collector → Normalisation → Validation → Persistance pour "
                    + "chaque collecteur enregistré, et retourne le résultat détaillé par source. "
                    + "Déclenchement manuel uniquement (aucune planification automatique). Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Import exécuté ; voir le détail par source dans la réponse "
                        + "(un échec de collecte pour une source se traduit par un résultat dégradé, pas "
                        + "par une erreur HTTP)."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "409", description = "Un import est déjà en cours (planifié ou manuel).")
    })
    @PostMapping
    public ResponseEntity<List<ImportResult>> triggerImport() {
        List<ImportResult> results = importService.importAll();
        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Découvre les agendas OpenAgenda officiels (réservé aux administrateurs)",
            description = "Liste les agendas officiels OpenAgenda, les plus actifs d'abord, et crée une source "
                    + "collectible pour chacun de ceux qui n'en ont pas encore. Ne lance aucun import : les "
                    + "nouvelles sources sont collectées à l'import suivant. Un agenda déjà présent comme "
                    + "source, même inactive, n'est ni recréé ni modifié.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Découverte effectuée ; voir le détail dans la réponse."),
        @ApiResponse(responseCode = "400", description = "Le paramètre 'limit' doit être positif."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "502",
                description = "OpenAgenda injoignable, ou clé API non configurée.")
    })
    @PostMapping("/discover-agendas")
    public ResponseEntity<Object> discoverAgendas(
            @Parameter(description = "Nombre maximum d'agendas à examiner. Par défaut, tous les agendas "
                    + "accessibles dans l'annuaire officiel.")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest httpRequest) {
        if (limit != null && limit < 1) {
            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    "Le paramètre 'limit' doit être positif.", httpRequest);
        }
        try {
            int effectiveLimit = limit == null ? Integer.MAX_VALUE : limit;
            return ResponseEntity.ok(agendaDiscoveryService.discoverOfficialAgendas(effectiveLimit));
        } catch (CollectorException exception) {
            return errorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Un import est déjà en cours (planifié ou manuel) : 409 plutôt qu'un second import en parallèle. */
    @ExceptionHandler(ImportAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleImportAlreadyRunning(
            ImportAlreadyRunningException exception, HttpServletRequest httpRequest) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(), httpRequest.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

}
