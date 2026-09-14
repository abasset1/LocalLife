package com.locallife.backend.like.infrastructure;

import com.locallife.backend.like.domain.ActivityLike;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository ActivityLike (LS-002). Étend {@link Repository}
 * (interface marqueur) plutôt que {@code CrudRepository}, même
 * convention que les autres repositories du projet : seules les
 * méthodes explicitement listées ici sont disponibles.
 *
 * {@code deleteByUserIdAndActivityId} nécessite {@code @Modifying}
 * (contrairement à {@code save}/{@code existsBy...}) : Spring Data JDBC
 * ne dérive pas automatiquement les méthodes de suppression comme
 * Spring Data JPA, une requête explicite est nécessaire.
 */
public interface ActivityLikeRepository extends Repository<ActivityLike, Long> {

    ActivityLike save(ActivityLike activityLike);

    boolean existsByUserIdAndActivityId(Long userId, Long activityId);

    @Modifying
    @Query("DELETE FROM activity_likes WHERE user_id = :userId AND activity_id = :activityId")
    void deleteByUserIdAndActivityId(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /**
     * Sous-ensemble de {@code activityIds} likés par {@code userId}
     * (LS-002) : utilisé par {@code ActivityController} pour peupler
     * {@code ActivityResponse#likedByCurrentUser} sur une page de
     * résultats {@code /nearby}/{@code /within-bounds}, en une seule
     * requête plutôt qu'un aller-retour par activité affichée.
     */
    @Query("SELECT activity_id FROM activity_likes WHERE user_id = :userId AND activity_id IN (:activityIds)")
    List<Long> findLikedActivityIds(@Param("userId") Long userId, @Param("activityIds") List<Long> activityIds);
}
