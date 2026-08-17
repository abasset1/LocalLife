package com.locallife.backend.source.api;

import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * Non protégé (comme {@code CategoryController}) : une source n'est pas
 * une donnée sensible, et {@code sourceId} est déjà visible sans
 * restriction dans toute réponse contenant une {@code Activity}.
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

}
