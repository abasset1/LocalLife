package com.locallife.backend.subscription.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.subscription.api.SubscriptionResponses.AdminSubscriptionResponse;
import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
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
 * Abonnements côté administration (BILLING-01, ADR-0014) : demandes en attente, activation (avec une date de fin
 * facultative), refus motivé, retrait d'un accès. Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
public class AdminSubscriptionController {

    private static final int MAX_LIMIT = 200;

    private final SubscriptionService service;

    public AdminSubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /** Décision : date de fin d'accès ({@code yyyy-MM-dd}, facultative) ou motif. */
    public record DecisionBody(String endsOn, String reason) {
    }

    @Operation(summary = "Abonnements", description = "Par défaut les demandes en attente (REQUESTED).")
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> {
            Set<SubscriptionStatus> statuses = EnumSet.noneOf(SubscriptionStatus.class);
            try {
                if (status == null || status.isBlank()) {
                    statuses.add(SubscriptionStatus.REQUESTED);
                } else {
                    Arrays.stream(status.split(",")).map(value -> SubscriptionStatus.valueOf(
                            value.trim().toUpperCase(Locale.ROOT))).forEach(statuses::add);
                }
            } catch (IllegalArgumentException exception) {
                throw ProActionException.invalid("Statut inconnu : " + status + ".");
            }
            int effectiveLimit = limit == null ? 50 : limit;
            int effectiveOffset = offset == null ? 0 : offset;
            if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT || effectiveOffset < 0) {
                throw ProActionException.invalid("'limit' doit être entre 1 et " + MAX_LIMIT + ", 'offset' positif.");
            }
            return ResponseEntity.ok(service.findByStatuses(statuses, effectiveLimit, effectiveOffset).stream()
                    .map(AdminSubscriptionResponse::from).toList());
        });
    }

    @Operation(summary = "Activer une demande", description = "endsOn : dernier jour d'accès (inclus) ; absent, "
            + "l'abonnement court jusqu'à résiliation. Remplace l'abonnement en cours de l'utilisateur, s'il y en "
            + "a un.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> {
            LocalDate endsOn;
            try {
                endsOn = body == null || body.endsOn() == null || body.endsOn().isBlank() ? null
                        : LocalDate.parse(body.endsOn().trim());
            } catch (DateTimeParseException exception) {
                throw ProActionException.invalid("Date de fin invalide : yyyy-MM-dd attendu.");
            }
            return ResponseEntity.ok(AdminSubscriptionResponse.from(service.approve(adminId(authentication), id,
                    endsOn)));
        });
    }

    @Operation(summary = "Refuser une demande", description = "Motif obligatoire, visible de l'utilisateur.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(AdminSubscriptionResponse.from(
                service.reject(adminId(authentication), id, body == null ? null : body.reason()))));
    }

    @Operation(summary = "Retirer un accès", description = "Met fin immédiatement à un abonnement en cours.")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Object> revoke(@PathVariable Long id, @RequestBody(required = false) DecisionBody body,
            Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(AdminSubscriptionResponse.from(
                service.revoke(adminId(authentication), id, body == null ? null : body.reason()))));
    }

    private static Long adminId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
