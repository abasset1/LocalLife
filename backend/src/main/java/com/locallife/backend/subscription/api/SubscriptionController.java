package com.locallife.backend.subscription.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.subscription.api.SubscriptionResponses.OverviewResponse;
import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abonnement de l'utilisateur connecté (BILLING-01, ADR-0014) : consulter son offre, souscrire Premium ou Pro,
 * retirer une ancienne demande, résilier. Aucun paiement branché : la souscription est activée tout de suite.
 * L'utilisateur est toujours celui du JWT, jamais un identifiant reçu.
 */
@RestController
@RequestMapping("/api/v1/users/me/subscription")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /** Offre demandée : {@code PREMIUM} ou {@code PRO}. */
    public record PlanRequest(String plan) {
    }

    @Operation(summary = "Mon abonnement", description = "Offre effective (FREE, PREMIUM ou PRO), abonnement en cours "
            + "avec sa date de fin d'accès, demande en attente et historique récent.")
    @GetMapping
    public ResponseEntity<Object> mine(Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(OverviewResponse.from(
                service.overview(userId(authentication)))));
    }

    @Operation(summary = "Souscrire Premium ou Pro", description = "Activation immédiate, sans échéance, comme si "
            + "le paiement était validé (aucun prestataire branché) ; remplace l'offre en cours. 409 si l'offre est "
            + "déjà active. /request est l'ancien chemin, conservé pour les applications déjà installées.")
    @PostMapping({"/subscribe", "/request"})
    public ResponseEntity<Object> request(@RequestBody(required = false) PlanRequest body,
            Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(OverviewResponse.from(
                service.subscribe(userId(authentication), plan(body)))));
    }

    @Operation(summary = "Retirer ma demande")
    @PostMapping("/withdraw")
    public ResponseEntity<Object> withdraw(Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(OverviewResponse.from(
                service.withdraw(userId(authentication)))));
    }

    @Operation(summary = "Résilier mon abonnement", description = "L'accès dure jusqu'à la fin de la période en "
            + "cours (accessEndsAt) ; sans période, il s'arrête tout de suite.")
    @PostMapping("/cancel")
    public ResponseEntity<Object> cancel(Authentication authentication, HttpServletRequest request) {
        return SubscriptionResponses.handle(request, () -> ResponseEntity.ok(OverviewResponse.from(
                service.cancel(userId(authentication)))));
    }

    private static Plan plan(PlanRequest body) {
        try {
            return Plan.valueOf(body == null || body.plan() == null ? "" : body.plan().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ProActionException.invalid("Offre inconnue : PREMIUM ou PRO.");
        }
    }

    private static Long userId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
