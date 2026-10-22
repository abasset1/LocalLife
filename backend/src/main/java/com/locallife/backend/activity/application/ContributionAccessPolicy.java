package com.locallife.backend.activity.application;

import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.infrastructure.AccountRepository;
import org.springframework.stereotype.Service;

/**
 * Droit de proposer ou de modifier une activité (décision du 21/10/2026, {@code ROLES_ET_PERMISSIONS.md} § 3 bis) :
 * offre Premium ou Pro en cours, ou rôle {@code PRO}/{@code ADMIN}. Un compte gratuit ne contribue pas. Sans ce droit,
 * l'auteur garde la main sur ses contributions existantes pour les consulter, les annuler ou les supprimer, mais plus
 * pour les modifier. Rôle et offre relus en base à chaque appel : un abonnement qui expire retire le droit
 * immédiatement.
 */
@Service
public class ContributionAccessPolicy {

    public static final String DENIED_MESSAGE =
            "Proposer ou modifier une activité est réservé aux offres Premium et Pro. Passe à Premium depuis ton "
                    + "abonnement pour contribuer.";

    private final AccountRepository accountRepository;
    private final SubscriptionService subscriptionService;

    public ContributionAccessPolicy(AccountRepository accountRepository, SubscriptionService subscriptionService) {
        this.accountRepository = accountRepository;
        this.subscriptionService = subscriptionService;
    }

    public boolean canContribute(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean byRole = accountRepository.findAccess(userId)
                .map(access -> access.role() == Role.PRO || access.role() == Role.ADMIN)
                .orElse(false);
        if (byRole) {
            return true;
        }
        Plan plan = subscriptionService.effectivePlan(userId);
        return plan == Plan.PREMIUM || plan == Plan.PRO;
    }
}
