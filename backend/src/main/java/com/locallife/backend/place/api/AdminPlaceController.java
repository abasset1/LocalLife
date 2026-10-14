package com.locallife.backend.place.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.place.api.AdminPlaceResponse.BulkModerationRequest;
import com.locallife.backend.place.api.AdminPlaceResponse.ModerationResult;
import com.locallife.backend.place.application.PlaceImportReport;
import com.locallife.backend.place.application.PlaceImportService;
import com.locallife.backend.place.application.PlaceMatchReportService;
import com.locallife.backend.place.application.PlaceModerationService;
import com.locallife.backend.place.application.PlaceModerationService.ModerationPage;
import com.locallife.backend.place.domain.PlaceMatcher;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationQuery;
import com.locallife.backend.source.application.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration des lieux permanents (ADR-0011, phase B) : file de modération, publication et refus (unitaires
 * ou groupés), essai à blanc, import et activation vérifiée d'une source de lieux. Réservé au rôle {@code ADMIN}
 * ({@code /api/v1/admin/**}, voir {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/admin/places")
public class AdminPlaceController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final PlaceModerationService moderationService;
    private final PlaceImportService importService;
    private final SourceService sourceService;
    private final PlaceMatchReportService matchReportService;

    public AdminPlaceController(
            PlaceModerationService moderationService, PlaceImportService importService, SourceService sourceService,
            PlaceMatchReportService matchReportService) {
        this.moderationService = moderationService;
        this.importService = importService;
        this.sourceService = sourceService;
        this.matchReportService = matchReportService;
    }

    @Operation(summary = "Rapprochement lieux d'événement ↔ lieux permanents (lecture seule)",
            description = "Mesure sans écriture (ADR-0011, § 2.3) : paires classées CERTAIN, PROBABLE ou AMBIGUOUS, "
                    + "totaux par niveau. Aucun lieu n'est modifié ni relié.")
    @GetMapping("/matches")
    public ResponseEntity<Object> matches(
            @RequestParam(required = false) String department,
            @Parameter(description = "CERTAIN, PROBABLE ou AMBIGUOUS.") @RequestParam(required = false)
            String level,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        PlaceMatcher.Level onlyLevel;
        try {
            onlyLevel = level == null ? null : PlaceMatcher.Level.valueOf(level.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "Niveau inconnu : " + level + ".", request);
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            return error(HttpStatus.BAD_REQUEST, "'limit' doit être entre 1 et " + MAX_LIMIT + ".", request);
        }
        return ResponseEntity.ok(PlaceMatchResponse.from(
                matchReportService.report(blankToNull(department), onlyLevel, effectiveLimit)));
    }

    @Operation(summary = "File de modération des lieux",
            description = "Lieux permanents filtrés par statut (PENDING par défaut), type, département (préfixe de "
                    + "code postal) et présence de signalements, avec leurs signalements.")
    @GetMapping
    public ResponseEntity<Object> list(
            @Parameter(description = "PENDING, PUBLISHED, REJECTED ou ARCHIVED.") @RequestParam(required = false)
            String status,
            @Parameter(description = "Type de lieu (ex. Museum).") @RequestParam(required = false) String type,
            @Parameter(description = "Département, préfixe de code postal (ex. 84).") @RequestParam(required = false)
            String department,
            @Parameter(description = "true : seulement les lieux signalés ; false : seulement les autres.")
            @RequestParam(required = false) Boolean flagged,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        PlaceStatus placeStatus;
        try {
            placeStatus = status == null ? PlaceStatus.PENDING : PlaceStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "Statut inconnu : " + status + ".", request);
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        int effectiveOffset = offset == null ? 0 : offset;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT || effectiveOffset < 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "'limit' doit être entre 1 et " + MAX_LIMIT + ", 'offset' positif ou nul.", request);
        }
        ModerationPage page = moderationService.find(new ModerationQuery(
                placeStatus, blankToNull(type), blankToNull(department), flagged, effectiveLimit, effectiveOffset));
        return ResponseEntity.ok(new AdminPlaceResponse.Page(
                page.items().stream().map(AdminPlaceResponse::from).toList(), page.total(), effectiveLimit,
                effectiveOffset));
    }

    @Operation(summary = "Publier un lieu", description = "Depuis PENDING ou REJECTED. 409 sinon, 404 si inconnu.")
    @PatchMapping("/{id}/publish")
    public ResponseEntity<Object> publish(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return moderateOne(id, PlaceStatus.PUBLISHED, authentication, request);
    }

    @Operation(summary = "Refuser un lieu",
            description = "Depuis PENDING ou PUBLISHED ; un lieu refusé n'est jamais reproposé. 409 sinon.")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return moderateOne(id, PlaceStatus.REJECTED, authentication, request);
    }

    @Operation(summary = "Action groupée",
            description = "Publie (PUBLISH) ou refuse (REJECT) jusqu'à 500 lieux ; renvoie les ids modifiés "
                    + "(les transitions non permises sont ignorées).")
    @PostMapping("/moderation")
    public ResponseEntity<Object> moderate(@RequestBody BulkModerationRequest body, Authentication authentication,
            HttpServletRequest request) {
        if (body == null || body.ids() == null || body.ids().isEmpty() || body.action() == null) {
            return error(HttpStatus.BAD_REQUEST, "'ids' et 'action' (PUBLISH ou REJECT) sont obligatoires.", request);
        }
        PlaceStatus target = switch (body.action().toUpperCase(Locale.ROOT)) {
            case "PUBLISH" -> PlaceStatus.PUBLISHED;
            case "REJECT" -> PlaceStatus.REJECTED;
            default -> null;
        };
        if (target == null) {
            return error(HttpStatus.BAD_REQUEST, "Action inconnue : PUBLISH ou REJECT.", request);
        }
        try {
            List<Long> applied = moderationService.moderate(body.ids(), target, adminId(authentication));
            return ResponseEntity.ok(new ModerationResult(body.ids(), applied));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
        }
    }

    @Operation(summary = "Essai à blanc d'une source de lieux", description = "Aucune écriture. 404 si la source "
            + "n'existe pas ou n'est pas une source de lieux.")
    @PostMapping("/sources/{sourceId}/dry-run")
    public ResponseEntity<PlaceImportReport> dryRun(@PathVariable Long sourceId) {
        return importService.run(sourceId, true).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Importer une source de lieux",
            description = "Import réel ; seulement pour une source active (409 sinon : passer par l'activation).")
    @PostMapping("/sources/{sourceId}/import")
    public ResponseEntity<Object> runImport(@PathVariable Long sourceId, HttpServletRequest request) {
        var source = sourceService.getSourceById(sourceId)
                .filter(candidate -> PlaceImportService.PLACE_SOURCE_TYPE.equals(candidate.type()));
        if (source.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!ACTIVE_STATUS.equals(source.get().status())) {
            return error(HttpStatus.CONFLICT,
                    "Source inactive : lancer d'abord l'essai à blanc et l'activation vérifiée.", request);
        }
        return importService.run(sourceId, false).<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Activer une source de lieux après vérification",
            description = "Essai à blanc, puis activation seulement si le verdict est OK (409 sinon, avec le bilan).")
    @PostMapping("/sources/{sourceId}/activate")
    public ResponseEntity<PlaceImportReport> activate(@PathVariable Long sourceId) {
        return moderationService.activate(sourceId)
                .map(report -> report.verdict() == PlaceImportReport.Verdict.OK
                        ? ResponseEntity.ok(report)
                        : ResponseEntity.status(HttpStatus.CONFLICT).body(report))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Object> moderateOne(
            Long id, PlaceStatus target, Authentication authentication, HttpServletRequest request) {
        if (moderationService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Long> applied = moderationService.moderate(List.of(id), target, adminId(authentication));
        if (applied.isEmpty()) {
            return error(HttpStatus.CONFLICT, "Transition non permise depuis le statut actuel du lieu.", request);
        }
        return ResponseEntity.ok(moderationService.findById(id).map(PlaceResponse::from).orElseThrow());
    }

    private static Long adminId(Authentication authentication) {
        return authentication instanceof JwtAuthentication jwt ? jwt.getUserId() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
