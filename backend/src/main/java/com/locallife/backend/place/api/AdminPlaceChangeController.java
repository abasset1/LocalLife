package com.locallife.backend.place.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.place.api.ProResponses.AdminPlaceChangeResponse;
import com.locallife.backend.place.api.ProResponses.ManagerResponse;
import com.locallife.backend.place.api.ProResponses.PlaceChangeReviewResponse;
import com.locallife.backend.place.application.ManagedPlaceService;
import com.locallife.backend.place.infrastructure.PlaceChangeRepository.ChangeStatus;
import com.locallife.backend.pro.application.ProActionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
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
 * Lieux gérés, côté administration (décisions du 22/10/2026) : validation des modifications proposées par les
 * gestionnaires d'un lieu publié, et retrait d'un gestionnaire. Réservé au rôle {@code ADMIN}
 * ({@code /api/v1/admin/**}) ; un administrateur ne valide jamais sa propre proposition.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminPlaceChangeController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ManagedPlaceService service;

    public AdminPlaceChangeController(ManagedPlaceService service) {
        this.service = service;
    }

    /** Motif d'un refus ou d'un retrait. */
    public record ReasonBody(String reason) {
    }

    @Operation(summary = "Propositions de modification de lieux",
            description = "Par défaut celles en attente, les plus anciennes d'abord.")
    @GetMapping("/place-changes")
    public ResponseEntity<Object> queue(
            @Parameter(description = "Statuts séparés par des virgules.") @RequestParam(required = false)
            String status,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        Set<ChangeStatus> statuses;
        try {
            statuses = status == null || status.isBlank() ? EnumSet.of(ChangeStatus.PENDING)
                    : EnumSet.copyOf(Arrays.stream(status.split(","))
                            .map(value -> ChangeStatus.valueOf(value.trim().toUpperCase(Locale.ROOT))).toList());
        } catch (IllegalArgumentException exception) {
            return ProResponses.error(HttpStatus.BAD_REQUEST, "Statut inconnu : " + status + ".", request);
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        int effectiveOffset = offset == null ? 0 : offset;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT || effectiveOffset < 0) {
            return ProResponses.error(HttpStatus.BAD_REQUEST,
                    "'limit' doit être entre 1 et " + MAX_LIMIT + ", 'offset' positif.", request);
        }
        return ResponseEntity.ok(service.queue(statuses, effectiveLimit, effectiveOffset).stream()
                .map(AdminPlaceChangeResponse::from).toList());
    }

    @Operation(summary = "Dossier d'une proposition", description = "La proposition, la fiche actuelle et les "
            + "gestionnaires du lieu.")
    @GetMapping("/place-changes/{id}")
    public ResponseEntity<Object> review(@PathVariable Long id, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(PlaceChangeReviewResponse.from(service.review(id))));
    }

    @Operation(summary = "Valider une proposition", description = "La fiche publiée prend les valeurs proposées, "
            + "qui priment ensuite sur les réimports. 403 pour sa propre proposition.")
    @PostMapping("/place-changes/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(AdminPlaceChangeResponse.from(
                service.approve(adminId(authentication), id))));
    }

    @Operation(summary = "Refuser une proposition", description = "Motif obligatoire, envoyé au demandeur.")
    @PostMapping("/place-changes/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable Long id, @RequestBody(required = false) ReasonBody body,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(AdminPlaceChangeResponse.from(
                service.reject(adminId(authentication), id, body == null ? null : body.reason()))));
    }

    @Operation(summary = "Gestionnaires actifs d'un lieu")
    @GetMapping("/places/{placeId}/managers")
    public ResponseEntity<Object> managers(@PathVariable Long placeId, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(service.managers(placeId).stream()
                .map(ManagerResponse::from).toList()));
    }

    @Operation(summary = "Retirer un gestionnaire", description = "Motif obligatoire, envoyé à l'intéressé ; ses "
            + "propositions en attente sur ce lieu sont retirées.")
    @PostMapping("/places/{placeId}/managers/{userId}/revoke")
    public ResponseEntity<Object> revoke(@PathVariable Long placeId, @PathVariable Long userId,
            @RequestBody(required = false) ReasonBody body, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> {
            service.revokeManager(adminId(authentication), placeId, userId, body == null ? null : body.reason());
            return ResponseEntity.noContent().build();
        });
    }

    private static ResponseEntity<Object> handle(HttpServletRequest request, Supplier<ResponseEntity<Object>> action) {
        try {
            return action.get();
        } catch (ProActionException exception) {
            return ProResponses.error(exception, request);
        }
    }

    private static Long adminId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
