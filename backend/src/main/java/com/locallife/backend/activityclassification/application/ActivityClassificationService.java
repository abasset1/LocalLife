package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import com.locallife.backend.activityclassification.domain.ActivityAudience;
import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import com.locallife.backend.activityclassification.domain.ActivitySecondaryCategory;
import com.locallife.backend.activityclassification.infrastructure.ActivityActivityTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityAudienceRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityOrganizerTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivitySecondaryCategoryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Service de classification d'une activité (CAT-006, SPRINT_CATEG) : porte
 * les 5 dimensions du nouveau modèle (1 catégorie principale, 0..N
 * catégories secondaires, 0..N types, 0..N publics, 0..1 organisateur).
 *
 * <b>Correctif CAT-006</b> : la catégorie principale n'est plus portée par
 * une table séparée ({@code activity_primary_category}, supprimée par
 * {@code V37__make_primary_category_mandatory.sql}) mais directement par
 * la colonne {@code activity.primary_category_id}
 * ({@link Activity#primaryCategoryId()}), {@code NOT NULL} en base — une
 * véritable contrainte d'obligation, pas une convention applicative.
 * {@link #setPrimaryCategory} passe donc par {@link ActivityRepository},
 * pas par un repository de relation dédié. Les 4 autres dimensions
 * (0..N ou 0..1 par nature, jamais obligatoires) restent portées par
 * leurs tables de relation séparées, créées par
 * {@code V36__create_activity_classification_tables.sql}.
 *
 * Ce service ne valide pas qu'un {@code categoryId}/{@code activityTypeId}/
 * {@code audienceId}/{@code organizerTypeId} donné existe réellement dans
 * son référentiel (CAT-001 à CAT-004) : cette garantie est assurée par les
 * contraintes {@code REFERENCES} des migrations — un id inexistant échoue
 * au niveau base, pas ici.
 *
 * Remplacement complet pour les listes (0..N) : {@link #replaceSecondaryCategories},
 * {@link #replaceActivityTypes} et {@link #replaceAudiences} suppriment
 * puis réinsèrent l'ensemble — même patron que {@code ActivityTagService}/
 * {@code ImportService} pour les tags (LL-11009/LL-11011).
 */
@Service
public class ActivityClassificationService {

    private final ActivityRepository activityRepository;
    private final ActivitySecondaryCategoryRepository secondaryCategoryRepository;
    private final ActivityActivityTypeRepository activityTypeRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityOrganizerTypeRepository organizerTypeRepository;

    public ActivityClassificationService(
            ActivityRepository activityRepository,
            ActivitySecondaryCategoryRepository secondaryCategoryRepository,
            ActivityActivityTypeRepository activityTypeRepository,
            ActivityAudienceRepository audienceRepository,
            ActivityOrganizerTypeRepository organizerTypeRepository) {
        this.activityRepository = activityRepository;
        this.secondaryCategoryRepository = secondaryCategoryRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.audienceRepository = audienceRepository;
        this.organizerTypeRepository = organizerTypeRepository;
    }

    /**
     * Vue agrégée des 5 dimensions pour une activité (critère « le modèle
     * supporte toutes les dimensions »).
     *
     * @throws java.util.NoSuchElementException si {@code activityId} ne
     *         correspond à aucune activité — contrairement aux autres
     *         dimensions, la catégorie principale ne peut structurellement
     *         pas être absente d'une activité existante (contrainte
     *         {@code NOT NULL}), donc une activité introuvable est la
     *         seule raison pour laquelle elle manquerait ici.
     */
    public ActivityClassification getClassification(Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElseThrow();
        List<Long> secondaryCategoryIds = secondaryCategoryRepository.findByActivityId(activityId).stream()
                .map(ActivitySecondaryCategory::categoryId)
                .toList();
        List<Long> activityTypeIds = activityTypeRepository.findByActivityId(activityId).stream()
                .map(ActivityActivityType::activityTypeId)
                .toList();
        List<Long> audienceIds = audienceRepository.findByActivityId(activityId).stream()
                .map(ActivityAudience::audienceId)
                .toList();
        Long organizerTypeId = organizerTypeRepository.findByActivityId(activityId)
                .map(ActivityOrganizerType::organizerTypeId)
                .orElse(null);
        return new ActivityClassification(
                activity.primaryCategoryId(), secondaryCategoryIds, activityTypeIds, audienceIds, organizerTypeId);
    }

    /**
     * Fixe la catégorie principale (critère « obligatoire ») en mettant à
     * jour {@code activity.primary_category_id} — la contrainte
     * {@code NOT NULL} en base est la véritable application du caractère
     * obligatoire ; la vérification ici n'est qu'un échec rapide côté
     * application, pas le mécanisme qui garantit la règle.
     *
     * @throws IllegalArgumentException si {@code categoryId} est {@code null}
     * @throws java.util.NoSuchElementException si {@code activityId} ne
     *         correspond à aucune activité
     */
    public void setPrimaryCategory(Long activityId, Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La catégorie principale est obligatoire.");
        }
        Activity activity = activityRepository.findById(activityId).orElseThrow();
        activityRepository.save(withPrimaryCategoryId(activity, categoryId));
    }

    private Activity withPrimaryCategoryId(Activity activity, Long primaryCategoryId) {
        return new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                activity.status(), activity.sourceId(), activity.importKey(), activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy(), primaryCategoryId);
    }

    /**
     * Remplace l'ensemble des catégories secondaires d'une activité.
     *
     * @throws IllegalArgumentException si {@code categoryIds} contient la
     *         catégorie principale actuelle de l'activité — critère
     *         « relations cohérentes et sans doublons » : une catégorie ne
     *         doit pas être à la fois principale et secondaire pour la
     *         même activité. Les doublons internes à {@code categoryIds}
     *         sont silencieusement dédupliqués, pas rejetés : seule la
     *         redondance avec la catégorie principale est une erreur.
     * @throws java.util.NoSuchElementException si {@code activityId} ne
     *         correspond à aucune activité
     */
    public void replaceSecondaryCategories(Long activityId, List<Long> categoryIds) {
        Activity activity = activityRepository.findById(activityId).orElseThrow();
        Set<Long> deduplicated = new LinkedHashSet<>(categoryIds);
        if (deduplicated.contains(activity.primaryCategoryId())) {
            throw new IllegalArgumentException(
                    "Une catégorie secondaire ne peut pas être identique à la catégorie principale.");
        }

        secondaryCategoryRepository.deleteByActivityId(activityId);
        for (Long categoryId : deduplicated) {
            secondaryCategoryRepository.save(new ActivitySecondaryCategory(null, activityId, categoryId));
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
     * Fixe le type d'organisateur (0..1 — contrairement à la catégorie
     * principale, {@code organizerTypeId == null} est un état définitif
     * valide, pas seulement temporaire). {@code null} supprime le type
     * d'organisateur existant sans le remplacer.
     */
    public void setOrganizerType(Long activityId, Long organizerTypeId) {
        organizerTypeRepository.deleteByActivityId(activityId);
        if (organizerTypeId != null) {
            organizerTypeRepository.save(new ActivityOrganizerType(null, activityId, organizerTypeId));
        }
    }

}
