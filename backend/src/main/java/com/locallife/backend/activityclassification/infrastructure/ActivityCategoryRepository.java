package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ActivityCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityCategory (issue #45). Relation 1..N : même patron que
 * {@code ActivityTagRepository} (LL-11009) — lecture par {@link #findByActivityId}, remplacement
 * complet géré par le service ({@code deleteByActivityId} puis une {@code save} par élément, dans
 * une même transaction).
 */
public interface ActivityCategoryRepository extends Repository<ActivityCategory, Long> {

    /** Ordre d'enregistrement (id) : ordre stable des catégories d'une activité. */
    List<ActivityCategory> findByActivityIdOrderByIdAsc(Long activityId);

    /** Catégories de plusieurs activités en une requête, dans l'ordre d'enregistrement. */
    List<ActivityCategory> findByActivityIdInOrderByIdAsc(Collection<Long> activityIds);

    ActivityCategory save(ActivityCategory activityCategory);

    void deleteByActivityId(Long activityId);

}
