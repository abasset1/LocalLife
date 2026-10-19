package com.locallife.backend.subscription.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.subscription.application.SubscriptionService.Overview;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.subscription.domain.Subscription;
import com.locallife.backend.subscription.domain.SubscriptionStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Réponses des abonnements (BILLING-01, ADR-0014). */
final class SubscriptionResponses {

    private SubscriptionResponses() {
    }

    /**
     * Abonnement. {@code accessEndsAt} : date de fin d'accès quand elle est connue (fin de période), {@code null}
     * pour un abonnement sans échéance.
     */
    record SubscriptionResponse(
            Long id, Plan plan, SubscriptionStatus status, OffsetDateTime startedAt, OffsetDateTime accessEndsAt,
            boolean cancelAtPeriodEnd, String decisionReason, OffsetDateTime requestedAt, OffsetDateTime updatedAt) {

        static SubscriptionResponse from(Subscription subscription) {
            return subscription == null ? null : new SubscriptionResponse(subscription.id(), subscription.plan(),
                    subscription.status(), subscription.currentPeriodStart(), subscription.currentPeriodEnd(),
                    subscription.cancelAtPeriodEnd(), subscription.decisionReason(), subscription.createdAt(),
                    subscription.updatedAt());
        }
    }

    /** Situation d'un utilisateur : offre effective, abonnement en cours, demande en attente, historique récent. */
    record OverviewResponse(
            Plan plan, SubscriptionResponse current, SubscriptionResponse pending,
            List<SubscriptionResponse> history) {

        static OverviewResponse from(Overview overview) {
            return new OverviewResponse(overview.plan(), SubscriptionResponse.from(overview.current()),
                    SubscriptionResponse.from(overview.pending()),
                    overview.history().stream().map(SubscriptionResponse::from).toList());
        }
    }

    /** Abonnement vu par un administrateur. */
    record AdminSubscriptionResponse(Long userId, String userEmail, SubscriptionResponse subscription) {

        static AdminSubscriptionResponse from(Subscription subscription) {
            return new AdminSubscriptionResponse(subscription.userId(), subscription.userEmail(),
                    SubscriptionResponse.from(subscription));
        }
    }

    static ResponseEntity<Object> handle(HttpServletRequest request, Supplier<ResponseEntity<Object>> action) {
        try {
            return action.get();
        } catch (ProActionException exception) {
            HttpStatus status = switch (exception.reason()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                    status.getReasonPhrase(), exception.getMessage(), request.getRequestURI()));
        }
    }
}
