package com.locallife.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.subscription.domain.Entitlements;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.subscription.domain.Subscription;
import com.locallife.backend.subscription.domain.SubscriptionStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EntitlementsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-19T12:00:00+02:00");

    private static Subscription subscription(SubscriptionStatus status, OffsetDateTime end) {
        return new Subscription(1L, 2L, "a@example.com", Plan.PRO, status, NOW.minusDays(10), end, false, "MANUAL",
                null, NOW.minusDays(11), NOW);
    }

    @Test
    void aRunningSubscription_ShouldGrantItsPlanUntilItsEnd() {
        assertThat(Entitlements.effectivePlan(null, NOW)).isEqualTo(Plan.FREE);
        assertThat(Entitlements.effectivePlan(subscription(SubscriptionStatus.ACTIVE, null), NOW)).isEqualTo(Plan.PRO);
        assertThat(Entitlements.effectivePlan(subscription(SubscriptionStatus.CANCELED, NOW.plusDays(3)), NOW))
                .isEqualTo(Plan.PRO);
        assertThat(Entitlements.effectivePlan(subscription(SubscriptionStatus.ACTIVE, NOW.minusSeconds(1)), NOW))
                .isEqualTo(Plan.FREE);
        assertThat(Entitlements.effectivePlan(subscription(SubscriptionStatus.EXPIRED, null), NOW))
                .isEqualTo(Plan.FREE);
    }
}
