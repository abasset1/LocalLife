package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import com.locallife.backend.activityclassification.domain.ActivityAudience;
import com.locallife.backend.activityclassification.domain.ActivityCategory;
import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import com.locallife.backend.activityclassification.infrastructure.ActivityActivityTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityAudienceRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityCategoryRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityOrganizerTypeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de classification d'une activité (CAT-006, SPRINT_CATEG) : porte
 * les dimensions du modèle (1..N catégories, 0..N types, 0..N publics, 0..1
 * organisateur).
 *
 * <b>Catégories (issue #45)</b> : une activité a 1..N catégories, toutes
 * équivalentes — aucune notion de catégorie principale ou secondaire. Elles
 * sont portées par la table {@code activity_category}
 * ({@code V42__activity_category_n_categories.sql}), où « au moins une
 * catégorie » est une contrainte réelle (trigger de contrainte différé,
 * vérifié au commit). D'où {@link #createActivity}, qui enregistre une
 * activité et ses catégories dans la même transaction, et
 * {@link #replaceCategories}, qui refuse une liste vide.
 *
 * Ce service ne valide pas qu'un {@code categoryId}/{@code activityTypeId}/
 * {@code audienceId}/{@code organizerTypeId} donné existe réellement dans
 * son référentiel (CAT-001 à CAT-004) : cette garantie est assurée par les
 * contraintes {@code REFERENCES} des migrations — un id inexistant échoue
 * au niveau base, pas ici.
 *
 * Remplacement complet pour les listes : {@link #replaceCategories},
 * {@link #replaceActivityTypes} et {@link #replaceAudiences} suppriment
 * puis réinsèrent l'ensemble — même patron que {@code ActivityTagService}/
 * {@code ImportService} pour les tags (LL-11009/LL-11011).
 */
@Service
public class ActivityClassificationService {

    private final ActivityRepository activityRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final ActivityActivityTypeRepository activityTypeRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityOrganizerTypeRepository organizerTypeRepository;

    public ActivityClassificationService(
            ActivityRepository activityRepository,
            ActivityCategoryRepository categoryRepository,
            ActivityActivityTypeRepository activityTypeRepository,
            ActivityAudienceRepository audienceRepository,
            ActivityOrganizerTypeRepository organizerTypeRepository) {
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.audienceRepository = audienceRepository;
        this.organizerTypeRepository = organizerTypeRepository;
    }

    /**
     * Vue agrégée des dimensions pour une activité (critère « le modèle
     * supporte toutes les dimensions »).
     *
     * @throws java.util.NoSuchElementException si {@code activityId} ne
     *         correspond à aucune activité
     */
    public ActivityClassification getClassification(Long activityId) {
        activityRepository.findById(activityId).orElseThrow();
        List<Long> activityTypeIds = activityTypeRepository.findByActivityId(activityId).stream()
                .map(ActivityActivityType::activityTypeId)
                .toList();
        List<Long> audienceIds = audienceRepository.findByActivityId(activityId).stream()
                .map(ActivityAudience::audienceId)
                .toList();
        Long organizerTypeId = organizerTypeRepository.findByActivityId(activityId)
                .map(ActivityOrganizerType::organizerTypeId)
                .orElse(null);
        return new ActivityClassification(getCategoryIds(activityId), activityTypeIds, audienceIds, organizerTypeId);
    }

    /** Catégories d'une activité, dans l'ordre d'enregistrement. */
    public List<Long> getCategoryIds(Long activityId) {
        return categoryRepository.findByActivityIdOrderByIdAsc(activityId).stream()
                .map(ActivityCategory::categoryId)
                .toList();
    }

    /**
     * Catégories de plusieurs activités, en une seule requête (listes de résultats de recherche) :
     * id d'activité → ids de catégories, dans l'ordre d'enregistrement. Une activité sans catégorie
     * est absente de la map.
     */
    public Map<Long, List<Long>> getCategoryIdsByActivityId(Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> categoryIdsByActivityId = new HashMap<>();
        for (ActivityCategory link : categoryRepository.findByActivityIdInOrderByIdAsc(activityIds)) {
            categoryIdsByActivityId.computeIfAbsent(link.activityId(), id -> new ArrayList<>())
                    .add(link.categoryId());
        }
        return categoryIdsByActivityId;
    }

    /**
     * Crée une activité avec ses catégories, dans une seule transaction : la
     * contrainte « au moins une catégorie » (V42) est vérifiée au commit, une
     * activité ne peut donc pas être enregistrée sans elles.
     *
     * @throws IllegalArgumentException si {@code categoryIds} est {@code null}
     *         ou vide (échec rapide côté application ; la contrainte en base
     *         reste la garantie réelle)
     */
    @Transactional
    public Activity createActivity(Activity activity, List<Long> categoryIds) {
        Set<Long> categories = requireCategories(categoryIds);
        Activity saved = activityRepository.save(activity);
        saveCategories(saved.id(), categories);
        return saved;
    }

    /**
     * Remplace l'ensemble des catégories d'une activité (doublons
     * dédupliqués, ordre conservé).
     *
     * @throws IllegalArgumentException si {@code categoryIds} est {@code null}
     *         ou vide : une activité a au moins une catégorie
     * @throws java.util.NoSuchElementException si {@code activityId} ne
     *         correspond à aucune activité
     */
    @Transactional
    public void replaceCategories(Long activityId, List<Long> categoryIds) {
        Set<Long> categories = requireCategories(categoryIds);
        activityRepository.findById(activityId).orElseThrow();
        categoryRepository.deleteByActivityId(activityId);
        saveCategories(activityId, categories);
    }

    private static Set<Long> requireCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Une activité doit avoir au moins une catégorie.");
        }
        if (categoryIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Une catégorie ne peut pas être nulle.");
        }
        return new LinkedHashSet<>(categoryIds);
    }

    private void saveCategories(Long activityId, Set<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            categoryRepository.save(new ActivityCategory(null, activityId, categoryId));
        }
    }

    /** Remplace l'ensemble des types d'une activité (doublons dédupliqués). */
    public void replaceActivityTypes(Long activityId, List<Long> activityTypeIds) {
        Set<Long> deduplicated = new LinkedHashSet<>(activityTypeIds);
        activityTypeRepository.deleteByActivityId(activityId);
        for (Long activityTypeId : deduplicated) {
            activityTypeRepository.save(new ActivityActivityType(null, activityId, activityTypeId));
        }
    }

    /** Remplace l'ensemble des publics d'une activité (doublons dédupliqués). */
    public void replaceAudiences(Long activityId, List<Long> audienceIds) {
        Set<Long> deduplicated = new LinkedHashSet<>(audienceIds);
        audienceRepository.deleteByActivityId(activityId);
        for (Long audienceId : deduplicated) {
            audienceRepository.save(new ActivityAudience(null, activityId, audienceId));
        }
    }

    /**
     * Fixe le type d'organisateur (0..1 — contrairement aux catégories,
     * {@code organizerTypeId == null} est un état définitif valide).
     * {@code null} supprime le type d'organisateur existant sans le remplacer.
     */
    public void setOrganizerType(Long activityId, Long organizerTypeId) {
        organizerTypeRepository.deleteByActivityId(activityId);
        if (organizerTypeId != null) {
            organizerTypeRepository.save(new ActivityOrganizerType(null, activityId, organizerTypeId));
        }
    }

}
