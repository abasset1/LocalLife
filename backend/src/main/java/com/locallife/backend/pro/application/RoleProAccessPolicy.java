package com.locallife.backend.pro.application;

import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Accès professionnel accordé par un abonnement Pro en cours (BILLING-01, ADR-0014), par le rôle {@code PRO}
 * (ADR-0012, décision D1, attribution directe par un administrateur), et aux administrateurs : un compte
 * {@code ADMIN} ne peut pas recevoir le rôle {@code PRO} et doit pourtant pouvoir saisir des lieux et des food trucks
 * (et vérifier l'espace professionnel). Un administrateur ne statue jamais sur sa propre demande de gestion
 * ({@code PlaceClaimService#moderate}). Le rôle est relu en base à chaque appel, pas dans le JWT : un rôle attribué
 * ou retiré par un administrateur prend effet immédiatement, sans attendre l'expiration du jeton.
 */
@Service
public class RoleProAccessPolicy implements ProAccessPolicy {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public RoleProAccessPolicy(UserRepository userRepository, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean hasProAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean byRole = userRepository.findById(userId)
                .map(user -> user.role() == Role.PRO || user.role() == Role.ADMIN).orElse(false);
        return byRole || subscriptionService.effectivePlan(userId) == Plan.PRO;
    }
}
