package com.locallife.backend.pro.application;

import com.locallife.backend.pro.infrastructure.ProAccountRepository;
import com.locallife.backend.pro.infrastructure.ProAccountRepository.ProRoleSource;
import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.user.domain.Role;
import org.springframework.stereotype.Service;

/**
 * Accès professionnel (décisions du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md} § « Professionnel ») :
 * <ul>
 *   <li>un administrateur (il ne peut pas recevoir le rôle {@code PRO} et doit pouvoir vérifier l'espace
 *       professionnel ; il ne valide jamais ses propres contenus) ;</li>
 *   <li>un rôle {@code PRO} attribué par un administrateur, indépendamment de tout abonnement ;</li>
 *   <li>une offre Pro en cours. Le rôle {@code PRO} posé à sa validation la suit : il ne compte pas seul, pour que
 *       l'accès s'arrête à l'échéance exacte, avant même le passage de l'expiration planifiée.</li>
 * </ul>
 * Relu en base à chaque appel, pas dans le JWT : un changement prend effet immédiatement.
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
        boolean byRole = accountRepository.findRoleState(userId)
                .map(state -> state.role() == Role.ADMIN
                        || state.role() == Role.PRO && state.source() != ProRoleSource.SUBSCRIPTION)
                .orElse(false);
        return byRole || subscriptionService.effectivePlan(userId) == Plan.PRO;
    }
}
