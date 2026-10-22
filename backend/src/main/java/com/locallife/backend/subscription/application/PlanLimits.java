package com.locallife.backend.subscription.application;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.infrastructure.AccountRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Limites par offre (décision du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md} § « Limites par offre ») :
 * <ul>
 *   <li>compte gratuit : {@value #FREE_MAX_LIKES} favoris, {@value #FREE_MAX_ALERTS} alerte, pas de contribution ;</li>
 *   <li>Premium : favoris illimités, {@value #PAID_MAX_ALERTS} alertes, {@value #PREMIUM_MONTHLY_CONTRIBUTIONS}
 *       nouvelles contributions par mois civil (heure de Paris), les contributions refusées ne comptant pas ;</li>
 *   <li>Pro (offre ou rôle) et administrateur : favoris illimités, {@value #PAID_MAX_ALERTS} alertes, contributions
 *       illimitées.</li>
 * </ul>
 * Une limite bloque seulement l'ajout : un compte déjà au-delà (offre terminée, limite apparue après coup) garde tout.
 * Rôle et offre relus en base à chaque appel.
 */
@Service
public class PlanLimits {

    public static final int FREE_MAX_LIKES = 5;
    public static final int FREE_MAX_ALERTS = 1;
    public static final int PAID_MAX_ALERTS = 10;
    public static final int PREMIUM_MONTHLY_CONTRIBUTIONS = 2;

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final AccountRepository accountRepository;
    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbcTemplate;

    public PlanLimits(AccountRepository accountRepository, SubscriptionService subscriptionService,
            JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.subscriptionService = subscriptionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Limites d'un compte ; {@code null} : illimité. {@code contributionsThisMonth} : compteur courant. */
    public record Limits(Integer maxLikes, int maxAlerts, Integer monthlyContributions, int contributionsThisMonth) {
    }

    public Limits limits(Long userId) {
        Tier tier = tier(userId);
        return new Limits(tier == Tier.FREE ? FREE_MAX_LIKES : null,
                tier == Tier.FREE ? FREE_MAX_ALERTS : PAID_MAX_ALERTS,
                switch (tier) {
                    case FREE -> 0;
                    case PREMIUM -> PREMIUM_MONTHLY_CONTRIBUTIONS;
                    case UNLIMITED -> null;
                },
                contributionsThisMonth(userId));
    }

    /** Avant d'ajouter un favori : {@code currentLikes} favoris déjà enregistrés. */
    public void checkCanAddLike(Long userId, long currentLikes) {
        if (tier(userId) == Tier.FREE && currentLikes >= FREE_MAX_LIKES) {
            throw new PlanLimitException("Le compte gratuit est limité à " + FREE_MAX_LIKES + " favoris : retire-en "
                    + "un ou passe à Premium pour des favoris illimités.");
        }
    }

    /** Avant de créer une alerte : {@code currentAlerts} alertes déjà enregistrées. */
    public void checkCanAddAlert(Long userId, long currentAlerts) {
        boolean free = tier(userId) == Tier.FREE;
        int max = free ? FREE_MAX_ALERTS : PAID_MAX_ALERTS;
        if (currentAlerts >= max) {
            throw new PlanLimitException(free
                    ? "Le compte gratuit est limité à " + FREE_MAX_ALERTS + " alerte : supprime-la ou passe à "
                            + "Premium pour en créer jusqu'à " + PAID_MAX_ALERTS + "."
                    : "Tu as atteint le nombre maximal d'alertes (" + PAID_MAX_ALERTS + ").");
        }
    }

    /** Avant une nouvelle contribution (le droit de contribuer est vérifié à part) ; les modifications ne comptent pas. */
    public void checkCanAddContribution(Long userId) {
        if (tier(userId) == Tier.PREMIUM && contributionsThisMonth(userId) >= PREMIUM_MONTHLY_CONTRIBUTIONS) {
            throw new PlanLimitException("L'offre Premium permet " + PREMIUM_MONTHLY_CONTRIBUTIONS + " nouvelles "
                    + "contributions par mois : tu pourras en proposer d'autres le mois prochain, ou sans limite "
                    + "avec l'offre Pro.");
        }
    }

    /** Contributions créées ce mois civil (heure de Paris), hors refusées. */
    public int contributionsThisMonth(Long userId) {
        OffsetDateTime monthStart = LocalDate.now(ZONE).withDayOfMonth(1).atStartOfDay(ZONE).toOffsetDateTime();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM activity
                WHERE created_by = ? AND created_at >= ? AND status <> 'REJECTED'
                """, Integer.class, userId, monthStart);
        return count == null ? 0 : count;
    }

    private enum Tier {
        FREE, PREMIUM, UNLIMITED
    }

    private Tier tier(Long userId) {
        boolean byRole = accountRepository.findAccess(userId)
                .map(access -> access.role() == Role.PRO || access.role() == Role.ADMIN).orElse(false);
        if (byRole) {
            return Tier.UNLIMITED;
        }
        return switch (subscriptionService.effectivePlan(userId)) {
            case PRO -> Tier.UNLIMITED;
            case PREMIUM -> Tier.PREMIUM;
            case FREE -> Tier.FREE;
        };
    }
}
