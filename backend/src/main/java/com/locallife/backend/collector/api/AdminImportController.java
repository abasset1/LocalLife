package com.locallife.backend.collector.api;

import com.locallife.backend.collector.application.AgendaActivationService;
import com.locallife.backend.collector.application.AgendaDiscoveryService;
import com.locallife.backend.collector.application.DiscoveredAgendaCatalogService;
import com.locallife.backend.collector.application.DiscoveryAlreadyRunningException;
import com.locallife.backend.collector.application.ImportAlreadyRunningException;
import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import com.locallife.backend.collector.application.SourceActivationService;
import com.locallife.backend.collector.application.SourceDryRunReport;
import com.locallife.backend.collector.application.SourceDryRunService;
import com.locallife.backend.collector.application.SourceSyncState;
import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.AgendaDiscoveryRepository.CatalogFilter;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.SourceSyncStateRepository;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AgendaActivationService agendaActivationService;
    private final DiscoveredAgendaCatalogService catalogService;
    private final SourceSyncStateRepository sourceSyncStateRepository;

    public AdminImportController(
            ImportService importService,
            AgendaDiscoveryService agendaDiscoveryService,
            SourceDryRunService sourceDryRunService,
            SourceActivationService sourceActivationService,
            AgendaActivationService agendaActivationService,
            DiscoveredAgendaCatalogService catalogService,
            SourceSyncStateRepository sourceSyncStateRepository) {
        this.importService = importService;
        this.agendaDiscoveryService = agendaDiscoveryService;
        this.sourceDryRunService = sourceDryRunService;
        this.sourceActivationService = sourceActivationService;
        this.agendaActivationService = agendaActivationService;
        this.catalogService = catalogService;
        this.sourceSyncStateRepository = sourceSyncStateRepository;
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
            summary = "Découvre les agendas OpenAgenda, par lots reprenables (réservé aux administrateurs)",
            description = "Traite un lot de pages de l'annuaire OpenAgenda et alimente le catalogue des agendas "
                    + "découverts. Ne crée AUCUNE source et ne lance aucun import : la création de sources passe "
                    + "par POST /discovered-agendas/sources. Reprend le parcours en cours du périmètre, ou en "
                    + "commence un. Le bilan indique si l'annuaire a été lu en entier (complete), ou si le "
                    + "parcours est reprenable (resumable) : relancer cet appel le poursuit.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lot traité ; voir statut et compteurs dans la réponse."),
        @ApiResponse(responseCode = "400", description = "Paramètre 'scope' ou 'maxPages' invalide."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "409", description = "Un lot de découverte est déjà en cours."),
        @ApiResponse(responseCode = "502", description = "Clé API OpenAgenda non configurée.")
    })
    @PostMapping("/discover-agendas")
    public ResponseEntity<Object> discoverAgendas(
            @Parameter(description = "Périmètre : ALL (tous les agendas listés par l'API avec la clé, défaut) ou "
                    + "OFFICIAL (agendas officiels seulement).")
            @RequestParam(required = false) String scope,
            @Parameter(description = "Pages de 100 agendas pour ce lot (défaut configuré, au plus "
                    + AgendaDiscoveryService.MAX_PAGES_PER_BATCH + ").")
            @RequestParam(required = false) Integer maxPages,
            @Parameter(description = "Abandonne le parcours en cours et repart du début de l'annuaire.")
            @RequestParam(defaultValue = "false") boolean restart,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(agendaDiscoveryService.discover(parseScope(scope), maxPages, restart));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (CollectorException exception) {
            return errorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), httpRequest);
        }
    }

    @Operation(summary = "État du dernier parcours de l'annuaire (réservé aux administrateurs)",
            description = "Ne lance rien. 204 si aucun parcours n'a encore eu lieu pour ce périmètre.")
    @GetMapping("/discover-agendas/latest")
    public ResponseEntity<Object> latestDiscovery(
            @RequestParam(required = false) String scope, HttpServletRequest httpRequest) {
        try {
            return agendaDiscoveryService.latest(parseScope(scope))
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    @Operation(summary = "Aperçu du catalogue des agendas découverts (réservé aux administrateurs)",
            description = "Nom, identifiant OpenAgenda, URL publique, statut officiel et source éventuelle de "
                    + "chaque agenda découvert, paginé. filter=NEW : agendas sans source ; KNOWN : avec source ; "
                    + "ALL : tous.")
    @GetMapping("/discovered-agendas")
    public ResponseEntity<Object> discoveredAgendas(
            @RequestParam(defaultValue = "NEW") String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean official,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        try {
            CatalogFilter catalogFilter = parseEnum(CatalogFilter.class, filter, "filter");
            return ResponseEntity.ok(catalogService.search(catalogFilter, q, official, page, size));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    @Operation(summary = "Crée les sources des agendas sélectionnés (réservé aux administrateurs)",
            description = "Crée une source collectable (API, active) pour chaque agenda du catalogue sélectionné "
                    + "qui n'en a pas encore. Lot borné (voir maxBatch) : une sélection plus grande est refusée "
                    + "en entier. Un agenda qui a déjà une source, même inactive, n'est ni recréé ni modifié. "
                    + "Aucun import n'est lancé.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bilan : créées, déjà connues, hors catalogue."),
        @ApiResponse(responseCode = "400", description = "Sélection vide ou plus grande que le lot autorisé."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @PostMapping("/discovered-agendas/sources")
    public ResponseEntity<Object> createSourcesFromCatalog(
            @RequestBody(required = false) AgendaSelectionRequest request, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(agendaActivationService.createSources(
                    request == null ? List.of() : request.agendaUids()));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    @Operation(summary = "Limites des opérations de découverte (réservé aux administrateurs)")
    @GetMapping("/discovery-settings")
    public DiscoverySettings discoverySettings() {
        return new DiscoverySettings(agendaDiscoveryService.defaultPagesPerBatch(),
                AgendaDiscoveryService.MAX_PAGES_PER_BATCH, agendaActivationService.maxBatch(),
                DiscoveredAgendaCatalogService.MAX_PAGE_SIZE);
    }

    @Operation(summary = "État de synchronisation des sources (réservé aux administrateurs)",
            description = "Par source collectable : dernier essai (statut SUCCESS, PARTIAL ou FAILED, message, "
                    + "pages, éléments reçus, durée) et dernier import complet réussi.")
    @GetMapping("/sync-states")
    public List<SourceSyncState> syncStates() {
        return sourceSyncStateRepository.findAll();
    }

    /** Sélection d'agendas du catalogue, par identifiant OpenAgenda. */
    public record AgendaSelectionRequest(List<String> agendaUids) {
    }

    /** Limites appliquées, pour que l'interface les affiche au lieu de les deviner. */
    public record DiscoverySettings(
            int defaultPagesPerBatch, int maxPagesPerBatch, int maxActivationBatch, int maxCatalogPageSize) {
    }

    private static DiscoveryScope parseScope(String scope) {
        return scope == null || scope.isBlank() ? DiscoveryScope.ALL : parseEnum(DiscoveryScope.class, scope, "scope");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Paramètre '" + name + "' invalide : " + value + ".");
        }
    }

    /** Un lot de découverte est déjà en cours : 409. */
    @ExceptionHandler(DiscoveryAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleDiscoveryAlreadyRunning(
            DiscoveryAlreadyRunningException exception, HttpServletRequest httpRequest) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(), httpRequest.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
