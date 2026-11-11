package com.locallife.backend.follow.application;

import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowedItem;
import com.locallife.backend.follow.infrastructure.FollowRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Suivre une activité, un lieu ou un food truck (cloche « Suivre ») pour être prévenu de ses mises à jour : réservé aux
 * offres Premium et Pro ({@link PlanLimits#checkCanFollow}). Seuls une activité visible du public (publiée ou annulée),
 * un lieu permanent publié et un food truck publié se suivent. Ne plus suivre reste toujours possible, quelle que soit
 * l'offre.
 */
@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final PlanLimits planLimits;

    public FollowService(FollowRepository followRepository, PlanLimits planLimits) {
        this.followRepository = followRepository;
        this.planLimits = planLimits;
    }

    /**
     * Idempotent : suivre une cible déjà suivie ne fait rien.
     *
     * @return {@code false} si la cible n'existe pas ou n'est pas visible (→ 404)
     * @throws com.locallife.backend.subscription.application.PlanLimitException compte gratuit
     */
    public boolean follow(Long userId, FollowTargetType type, Long targetId) {
        boolean followable = switch (type) {
            case ACTIVITY -> followRepository.isFollowableActivity(targetId);
            case PLACE -> followRepository.isFollowablePlace(targetId);
            case FOOD_TRUCK -> followRepository.isFollowableFoodTruck(targetId);
        };
        if (!followable) {
            return false;
        }
        if (!followRepository.exists(userId, type, targetId)) {
            planLimits.checkCanFollow(userId);
            followRepository.insert(userId, type, targetId);
        }
        return true;
    }

    /** Idempotent : ne plus suivre une cible non suivie ne fait rien. */
    public void unfollow(Long userId, FollowTargetType type, Long targetId) {
        followRepository.delete(userId, type, targetId);
    }

    public List<FollowedItem> findFollowed(Long userId) {
        return followRepository.findByUserId(userId);
    }
}
