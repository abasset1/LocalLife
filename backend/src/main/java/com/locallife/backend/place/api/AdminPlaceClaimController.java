package com.locallife.backend.place.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.place.api.ProResponses.AdminClaimResponse;
import com.locallife.backend.place.api.ProResponses.ClaimReviewResponse;
import com.locallife.backend.place.application.PlaceClaimService;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.place.domain.ClaimStatus;
import com.locallife.backend.place.domain.ClaimTransitions.Action;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modération des demandes de gestion de lieux (ADR-0012, parcours A). Réservé au rôle {@code ADMIN}
 * ({@code /api/v1/admin/**}, voir {@code SecurityConfig}). Chaque décision est tracée avec son auteur et son motif.
 */
@RestController
@RequestMapping("/api/v1/admin/place-claims")
public class AdminPlaceClaimController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final Set<ClaimStatus> DEFAULT_QUEUE = EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW);

    private final PlaceClaimService claimService;

    public AdminPlaceClaimController(PlaceClaimService claimService) {
        this.claimService = claimService;
    }

    /** Motif d'une décision : obligatoire pour refuser ou demander des informations. */
    public record DecisionBody(String reason) {
    }

    @Operation(summary = "File des demandes de gestion",
            description = "Par défaut les demandes envoyées ou en cours d'examen, les plus anciennes d'abord.")
    @GetMapping
    public ResponseEntity<Object> queue(
            @Parameter(description = "Statuts séparés par des virgules.") @RequestParam(required = false)
            String status,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        Set<ClaimStatus> statuses;
        try {
            statuses = status == null || status.isBlank() ? DEFAULT_QUEUE : EnumSet.copyOf(Arrays.stream(
                    status.split(",")).map(value -> ClaimStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                    .toList());
        } catch (IllegalArgumentException exception) {
            return ProResponses.error(HttpStatus.BAD_REQUEST, "Statut inconnu : " + status + ".", request);
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        int effectiveOffset = offset == null ? 0 : offset;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT || effectiveOffset < 0) {
            return ProResponses.error(HttpStatus.BAD_REQUEST,
                    "'limit' doit être entre 1 et " + MAX_LIMIT + ", 'offset' positif.", request);
        }
        return ResponseEntity.ok(claimService.queue(statuses, effectiveLimit, effectiveOffset).stream()
                .map(AdminClaimResponse::from).toList());
    }

    @Operation(summary = "Dossier d'une demande",
            description = "La demande, son historique, les autres demandes en cours sur le même lieu et ses "
                    + "gestionnaires actuels.")
    @GetMapping("/{id}")
    public ResponseEntity<Object> review(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ClaimReviewResponse.from(claimService.review(id)));
        } catch (ProActionException exception) {
            return ProResponses.error(exception, request);
        }
    }

    @Operation(summary = "Prendre en charge une demande")
    @PostMapping("/{id}/start-review")
    public ResponseEntity<Object> startReview(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return decide(id, Action.START_REVIEW, null, authentication, request);
    }

    @Operation(summary = "Approuver une demande", description = "Le demandeur devient gestionnaire du lieu ; la "
            + "fiche du lieu et les autres demandes ne changent pas.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return decide(id, Action.APPROVE, body, authentication, request);
    }

    @Operation(summary = "Refuser une demande", description = "Motif obligatoire, visible du demandeur.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return decide(id, Action.REJECT, body, authentication, request);
    }

    @Operation(summary = "Demander des informations", description = "Motif obligatoire, visible du demandeur.")
    @PostMapping("/{id}/request-info")
    public ResponseEntity<Object> requestInfo(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return decide(id, Action.REQUEST_INFO, body, authentication, request);
    }

    private ResponseEntity<Object> decide(Long id, Action action, DecisionBody body, Authentication authentication,
            HttpServletRequest request) {
        try {
            Long adminId = ((JwtAuthentication) authentication).getUserId();
            return ResponseEntity.ok(AdminClaimResponse.from(
                    claimService.moderate(adminId, id, action, body == null ? null : body.reason())));
        } catch (ProActionException exception) {
            return ProResponses.error(exception, request);
        }
    }
}
