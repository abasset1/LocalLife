package com.locallife.backend.source.api;

import com.locallife.backend.common.ErrorResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation des sources (LL-6007, Sprint 6).
 * Endpoint de consultation uniquement — même rôle et même structure que
 * {@code CategoryController} : aucune écriture, service existant
 * ({@link SourceService}, LL-5002) sans logique nouvelle.
 *
 * Comble le seul écart identifié pour le critère « source identifiable »
 * de LL-6007 : {@code Activity} porte un {@code sourceId} depuis LL-5008
 * (toujours renseigné, y compris pour les activités manuelles — source
 * réservée {@code MANUAL}, voir {@code SOURCE_CONTRACT.md}), mais rien ne
 * permettait jusqu'ici à un consommateur de l'API de résoudre cet id en
 * un nom/type lisible. Les trois autres critères du ticket (activité
 * manuelle conservée, activité importée conservée avec sa source, aucune
 * duplication) étaient déjà satisfaits par LL-5008/LL-5009
 * ({@code ImportService}, recherche scopée par {@code sourceId} +
 * {@code importKey}) — aucun changement nécessaire sur ce module.
 *
 * Non protégé en lecture (comme {@code CategoryController}) : une source
 * n'est pas une donnée sensible, et {@code sourceId} est déjà visible sans
 * restriction dans toute réponse contenant une {@code Activity}. Depuis
 * LL-EF-005, les trois méthodes d'écriture ({@code POST}/{@code PUT}/
 * {@code DELETE}) sont en revanche réservées au rôle {@code ADMIN}
 * ({@code SecurityConfig}) : elles permettent de configurer les agendas
 * réellement collectés par {@code ImportService} (voir
 * {@code OpenAgendaCollectorFactory}), un pouvoir qui ne doit pas être
 * ouvert à tout visiteur.
 *
 * ⚠️ Décision prise, non explicitement demandée par le ticket : exposer
 * la liste complète ({@code GET /api/v1/sources}, miroir exact de
 * {@code CategoryController#getAllCategories}) *et* la consultation par
 * id ({@code GET /api/v1/sources/{id}}, réutilise
 * {@link SourceService#getSourceById}, déjà existante mais jusqu'ici
 * inutilisée par aucun contrôleur). C'est cette seconde méthode qui
 * répond concrètement au besoin (résoudre le {@code sourceId} d'une
 * activité donnée) ; la liste complète est ajoutée pour rester cohérente
 * avec le seul autre module de référencement simple du projet
 * ({@code Category}), pas pour un besoin fonctionnel identifié.
 *
 * Annotations Swagger/OpenAPI ajoutées en LL-6011 (documentation de fin
 * de sprint), pour la même richesse de documentation générée que
 * {@code ActivityController}/{@code AdminActivityController} — Springdoc
 * documentait déjà cet endpoint automatiquement sans elles, mais avec des
 * descriptions génériques inférées des noms de méthode/paramètre.
 */
@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Operation(
            summary = "Liste toutes les sources",
            description = "Retourne toutes les sources connues (import externe ou saisie manuelle). "
                    + "Endpoint public, sans authentification.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès (peut être vide).")
    })
    @GetMapping
    public ResponseEntity<List<Source>> getAllSources() {
        List<Source> sources = sourceService.getAllSources();
        return ResponseEntity.ok(sources);
    }

    /**
     * Résout un {@code sourceId} (porté par {@code Activity} depuis
     * LL-5008) en source lisible. {@code 404} sans corps si aucune source
     * ne correspond — même convention que
     * {@code ActivityController#getActivityById}.
     */
    @Operation(
            summary = "Résout un identifiant de source",
            description = "Retourne la source correspondant à l'id fourni (utile pour résoudre le "
                    + "sourceId d'une activité en nom/type lisible). Endpoint public.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Source trouvée."),
        @ApiResponse(responseCode = "404", description = "Aucune source ne correspond à cet id.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Source> getSourceById(
            @Parameter(description = "Identifiant de la source à résoudre.", required = true)
            @PathVariable Long id) {
        return sourceService.getSourceById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Crée une source (LL-EF-005) — typiquement un agenda OpenAgenda
     * ({@code type="API"}, {@code agendaUid} renseigné), mais générique :
     * n'importe quel type ({@code API}/{@code RSS}/{@code MANUAL}) est
     * accepté, sans validation métier au-delà de {@code name}/{@code type}
     * non vides (voir {@link com.locallife.backend.source.application.SourceService#createSource}).
     * Réservé au rôle {@code ADMIN} ({@code SecurityConfig}).
     */
    @Operation(
            summary = "Crée une source",
            description = "Crée une nouvelle source (agenda OpenAgenda ou autre). Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Source créée avec succès."),
        @ApiResponse(responseCode = "400", description = "Requête invalide (nom ou type manquant).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @PostMapping
    public ResponseEntity<Object> createSource(
            @RequestBody CreateSourceRequest request, HttpServletRequest httpRequest) {
        try {
            Source created = sourceService.createSource(
                    request.name(), request.type(), request.url(), request.agendaUid(), request.regionFilter());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Met à jour une source existante (LL-EF-005), y compris son
     * {@code status} (ex. désactiver temporairement un agenda sans le
     * supprimer). Réservé au rôle {@code ADMIN}.
     */
    @Operation(
            summary = "Modifie une source",
            description = "Met à jour les informations d'une source existante. Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Source mise à jour avec succès."),
        @ApiResponse(responseCode = "400", description = "Requête invalide (nom ou type manquant).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune source ne correspond à cet id.")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateSource(
            @Parameter(description = "Identifiant de la source à modifier.", required = true)
            @PathVariable Long id,
            @RequestBody UpdateSourceRequest request,
            HttpServletRequest httpRequest) {
        try {
            return sourceService
                    .updateSource(id, request.name(), request.type(), request.url(), request.status(),
                            request.agendaUid(), request.regionFilter())
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    /**
     * Supprime une source (LL-EF-005, décision Alex : suppression
     * autorisée même si des activités y sont encore rattachées — voir
     * {@link com.locallife.backend.source.application.SourceService#deleteSource}
     * pour le détachement automatique vers la source réservée
     * {@code MANUAL}). La confirmation avant suppression (critère du
     * ticket) est une responsabilité du frontend (boîte de dialogue) —
     * cet endpoint exécute la suppression dès qu'il est appelé, sans
     * étape de confirmation supplémentaire côté backend.
     */
    @Operation(
            summary = "Supprime une source",
            description = "Supprime une source ; les activités qui lui étaient rattachées sont détachées vers "
                    + "la source réservée 'Saisie manuelle'. La source réservée 'MANUAL' elle-même ne peut pas "
                    + "être supprimée. Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Source supprimée avec succès."),
        @ApiResponse(responseCode = "400", description = "La source réservée 'MANUAL' ne peut pas être supprimée.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentification requise."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis."),
        @ApiResponse(responseCode = "404", description = "Aucune source ne correspond à cet id.")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSource(
            @Parameter(description = "Identifiant de la source à supprimer.", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            return sourceService.deleteSource(id)
                    .<ResponseEntity<Object>>map(source -> ResponseEntity.noContent().build())
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    public record CreateSourceRequest(String name, String type, String url, String agendaUid, String regionFilter) {
    }

    public record UpdateSourceRequest(
            String name, String type, String url, String status, String agendaUid, String regionFilter) {
    }

}
