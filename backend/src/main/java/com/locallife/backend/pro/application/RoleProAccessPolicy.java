package com.locallife.backend.pro.application;

import com.locallife.backend.pro.infrastructure.ProAccountRepository;
import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.user.domain.Role;
import org.springframework.stereotype.Service;

/**
 * Accès professionnel (décision du 02/11/2026, {@code ROLES_ET_PERMISSIONS.md}) : un compte de type Pro (offre Pro en
 * cours, jusqu'à son échéance exacte), ou un administrateur, qui doit pouvoir vérifier l'espace professionnel et ne
 * valide jamais ses propres contenus. Le rôle {@code PRO} n'existe plus (V81). Relu en base à chaque appel, pas dans
 * le JWT : un changement prend effet immédiatement.
 */
@Service
public class RoleProAccessPolicy implements ProAccessPolicy {

    private final ProAccountRepository accountRepository;
    private final SubscriptionService subscriptionService;

    public RoleProAccessPolicy(ProAccountRepository accountRepository, SubscriptionService subscriptionService) {
        this.accountRepository = accountRepository;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean hasProAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean admin = accountRepository.findRole(userId).map(role -> role == Role.ADMIN).orElse(false);
        return admin || subscriptionService.effectivePlan(userId) == Plan.PRO;
    }
}
