package com.locallife.backend.like.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.like.domain.ActivityLike;
import com.locallife.backend.like.infrastructure.ActivityLikeRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service métier pour liker/déliker une activité et consulter ses
 * favoris (LS-002).
 *
 * Dépend de {@link ActivityRepository} (module {@code activity}), pas
 * l'inverse — même direction que {@code auth} → {@code user} déjà
 * établie ailleurs dans le projet (voir {@code ARCHITECTURE.md}) :
 * {@code like} est une fonctionnalité qui porte sur une {@link Activity}
 * existante, dont elle a besoin pour valider qu'un like porte bien sur
 * une activité qui existe, et pour retourner les activités complètes
 * dans {@link #getLikedActivities}.
 */
@Service
public class LikeService {

    private final ActivityLikeRepository activityLikeRepository;
    private final ActivityRepository activityRepository;
    private final PlanLimits planLimits;

    public LikeService(ActivityLikeRepository activityLikeRepository, ActivityRepository activityRepository,
            PlanLimits planLimits) {
        this.activityLikeRepository = activityLikeRepository;
        this.activityRepository = activityRepository;
        this.planLimits = planLimits;
    }

    /**
     * Like une activité pour le compte d'un utilisateur. Idempotent : si
     * l'utilisateur a déjà liké cette activité, ne fait rien plutôt que
     * d'échouer — un bouton "J'aime" qui resterait dans un état "liké"
     * après un double-clic ou un rejeu réseau ne doit jamais produire
     * d'erreur visible (le critère d'acceptation "un utilisateur ne peut
     * pas créer plusieurs likes pour le même événement" porte sur
     * l'absence de doublon en base, garantie de toute façon par la
     * contrainte {@code UNIQUE (user_id, activity_id)} — voir
     * {@code V29__create_activity_likes_table.sql} — pas sur le fait de
     * rejeter un second appel).
     *
     * Un compte gratuit est limité à 5 favoris (décision du 22/10/2026,
     * {@link PlanLimits}) : au-delà, un nouveau like est refusé ; re-liker
     * une activité déjà likée reste sans effet.
     *
     * @return {@code false} si aucune activité ne correspond à
     *         {@code activityId} (→ {@code 404} côté contrôleur),
     *         {@code true} sinon (déjà liké, ou venant de l'être).
     * @throws com.locallife.backend.subscription.application.PlanLimitException limite de favoris atteinte
     */
    public boolean like(Long userId, Long activityId) {
        if (activityRepository.findById(activityId).isEmpty()) {
            return false;
        }
        if (!activityLikeRepository.existsByUserIdAndActivityId(userId, activityId)) {
            planLimits.checkCanAddLike(userId, activityLikeRepository.countByUserId(userId));
            activityLikeRepository.save(new ActivityLike(null, userId, activityId, LocalDateTime.now()));
        }
        return true;
    }

    /**
     * Retire le like d'un utilisateur sur une activité. Idempotent,
     * même raisonnement que {@link #like} : déliker une activité non
     * likée n'est pas une erreur, seulement un no-op.
     *
     * Ne vérifie volontairement pas que l'activité existe (contrairement
     * à {@link #like}) : si elle n'existe plus, il ne peut de toute façon
     * exister aucun like la concernant ({@code ON DELETE CASCADE}, voir
     * {@code V29__create_activity_likes_table.sql}) — la suppression est
     * un no-op naturel dans ce cas, pas la peine d'un aller-retour
     * supplémentaire pour le détecter.
     */
    public void unlike(Long userId, Long activityId) {
        activityLikeRepository.deleteByUserIdAndActivityId(userId, activityId);
    }

    /**
     * Activités actuellement likées par un utilisateur, les plus
     * récemment likées en premier — voir
     * {@link ActivityRepository#findLikedByUserId} pour le détail du
     * filtrage (statut {@code PUBLISHED} uniquement).
     */
    public List<Activity> getLikedActivities(Long userId) {
        return activityRepository.findLikedByUserId(userId);
    }

    /**
     * Sous-ensemble de {@code activityIds} likés par {@code userId} —
     * voir {@link ActivityLikeRepository#findLikedActivityIds}. Utilisé
     * par {@code ActivityController} pour peupler
     * {@code ActivityResponse#likedByCurrentUser} sur une page de
     * résultats.
     */
    public List<Long> findLikedActivityIds(Long userId, List<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return List.of();
        }
        return activityLikeRepository.findLikedActivityIds(userId, activityIds);
    }
}
