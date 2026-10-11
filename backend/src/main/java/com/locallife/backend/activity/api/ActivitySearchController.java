package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivitySearchService;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche textuelle globale (#91) : {@code GET /api/v1/activities/search}, publique. Contrôleur
 * distinct d'{@code ActivityController} : les endpoints existants ({@code /}, {@code /nearby},
 * {@code /within-bounds}) ne changent pas. Contrat : {@code docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md}.
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivitySearchController {

    private final ActivitySearchService searchService;

    public ActivitySearchController(ActivitySearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(
            summary = "Recherche textuelle globale dans les activités publiées",
            description = "Cherche dans le titre, la description, les catégories, la ville et l'adresse (casse, "
                    + "accents et ponctuation ignorés), sans rayon ni zone. Tri par niveau de pertinence, puis "
                    + "nombre de champs correspondants, puis distance à la position de référence si fournie, "
                    + "puis date de début et identifiant. Pagination par limit/offset avec le total.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page de résultats."),
        @ApiResponse(responseCode = "400", description = "Paramètre absent ou invalide.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<Object> search(
            @Parameter(description = "Texte cherché, 2 à 120 caractères après suppression des espaces autour.",
                    required = true)
            @RequestParam(required = false) String q,
            @Parameter(description = "Latitude de référence (tri par proximité), avec 'longitude'.")
            @RequestParam(required = false) String latitude,
            @Parameter(description = "Longitude de référence (tri par proximité), avec 'latitude'.")
            @RequestParam(required = false) String longitude,
            @Parameter(description = "Catégories séparées par des virgules (codes du référentiel ou catégorie "
                    + "libre), comme /nearby.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Date (yyyy-MM-dd) ; début de période si 'dateTo' est fourni. Absente : "
                    + "activités non terminées.")
            @RequestParam(required = false) String date,
            @Parameter(description = "Fin de période (yyyy-MM-dd), avec 'date', postérieure ou égale.")
            @RequestParam(required = false) String dateTo,
            @Parameter(description = "Taille de page, 1 à 100 (20 par défaut).")
            @RequestParam(required = false) String limit,
            @Parameter(description = "Décalage, 0 ou plus (0 par défaut).")
            @RequestParam(required = false) String offset,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(ActivitySearchResponse.from(
                    searchService.search(q, latitude, longitude, category, date, dateTo, limit, offset)));
        } catch (IllegalArgumentException exception) {
            ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(), exception.getMessage(), httpRequest.getRequestURI());
            return ResponseEntity.badRequest().body(body);
        }
    }
}
