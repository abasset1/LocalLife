package com.locallife.backend.subscription.domain;

import java.time.OffsetDateTime;

/** Abonnement d'un utilisateur (BILLING-01, ADR-0014), avec son email pour l'administration. */
public record Subscription(
        Long id,
        Long userId,
        String userEmail,
        Plan plan,
        SubscriptionStatus status,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String provider,
        String decisionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
