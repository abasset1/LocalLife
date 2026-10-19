package com.locallife.backend.subscription.domain;

import java.time.OffsetDateTime;

/**
 * Offre effective d'un utilisateur (BILLING-01, ADR-0014), en fonction pure : l'abonnement en cours ouvre ses droits
 * tant que sa période n'est pas échue, qu'il soit actif ou résilié ; sinon, offre gratuite. Une résiliation ou une
 * expiration retire donc les droits au moment prévu, et pas avant.
 */
public final class Entitlements {

    private Entitlements() {
    }

    public static Plan effectivePlan(Subscription current, OffsetDateTime now) {
        if (current == null) {
            return Plan.FREE;
        }
        boolean running = current.status() == SubscriptionStatus.ACTIVE
                || current.status() == SubscriptionStatus.CANCELED;
        boolean notYetEnded = current.currentPeriodEnd() == null || current.currentPeriodEnd().isAfter(now);
        return running && notYetEnded ? current.plan() : Plan.FREE;
    }
}
