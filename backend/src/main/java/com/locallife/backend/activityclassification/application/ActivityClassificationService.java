package com.locallife.backend.activityclassification.application;

import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import com.locallife.backend.activityclassification.domain.ActivityAudience;
import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import com.locallife.backend.activityclassification.domain.ActivityPrimaryCategory;
import com.locallife.backend.activityclassification.domain.ActivitySecondaryCategory;
import com.locallife.backend.activityclassification.infrastructure.ActivityActivityTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityAudienceRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityOrganizerTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityPrimaryCategoryRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivitySecondaryCategoryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Service de classification d'une activité (CAT-006, SPRINT_CATEG) :
 * porte les 5 dimensions du nouveau modèle (1 catégorie principale, 0..N
 * catégories secondaires, 0..N types, 0..N publics, 0..1 organisateur),
 * en s'appuyant sur les 5 tables de relation créées par
 * {@code V36__create_activity_classification_tables.sql}.
 *
 * Ce service gère uniquement les relations — il ne valide pas qu'un
 * {@code categoryId}/{@code activityTypeId}/{@code audienceId}/
 * {@code organizerTypeId} donné existe réellement dans son référentiel
 * (CAT-001 à CAT-004) : cette garantie est déjà assurée par les
 * contraintes {@code REFERENCES} de la migration — une tentative avec un
 * id inexistant échoue au niveau base, pas ici.
 *
 * Remplacement complet pour les listes (0..N) : {@link #replaceSecondaryCategories},
 * {@link #replaceActivityTypes} et {@link #replaceAudiences} suppriment
 * puis réinsèrent l'ensemble — même patron que {@code ActivityTagService}/
 * {@code ImportService} pour les tags (LL-11009/LL-11011), plutôt qu'un
 * ajout/retrait unitaire.
 */
@Service
public class ActivityClassificationService {

    private final ActivityPrimaryCategoryRepository primaryCategoryRepository;
    private final ActivitySecondaryCategoryRepository secondaryCategoryRepository;
    private final ActivityActivityTypeRepository activityTypeRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityOrganizerTypeRepository organizerTypeRepository;

    public ActivityClassificationService(
            ActivityPrimaryCategoryRepository primaryCategoryRepository,
            ActivitySecondaryCategoryRepository secondaryCategoryRepository,
            ActivityActivityTypeRepository activityTypeRepository,
            ActivityAudienceRepository audienceRepository,
            ActivityOrganizerTypeRepository organizerTypeRepository) {
        this.primaryCategoryRepository = primaryCategoryRepository;
        this.secondaryCategoryRepository = secondaryCategoryRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.audienceRepository = audienceRepository;
        this.organizerTypeRepository = organizerTypeRepository;
    }

    /**
     * Vue agrégée des 5 dimensions pour une activité (critère « le modèle
     * supporte toutes les dimensions »).
     */
    public ActivityClassification getClassification(Long activityId) {
        Long primaryCategoryId = primaryCategoryRepository.findByActivityId(activityId)
                .map(ActivityPrimaryCategory::categoryId)
                .orElse(null);
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
                primaryCategoryId, secondaryCategoryIds, activityTypeIds, audienceIds, organizerTypeId);
    }

    /**
     * Fixe la catégorie principale (critère « obligatoire ») : remplace
     * toute catégorie principale déjà définie pour cette activité.
     *
     * @throws IllegalArgumentException si {@code categoryId} est {@code null}
     *         — seul moyen, à ce niveau, d'appliquer le caractère
     *         obligatoire tant que la contrainte n'est pas portée par la
     *         base (voir la javadoc de {@link ActivityPrimaryCategory})
     */
    public void setPrimaryCategory(Long activityId, Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La catégorie principale est obligatoire.");
        }
        primaryCategoryRepository.deleteByActivityId(activityId);
        primaryCategoryRepository.save(new ActivityPrimaryCategory(null, activityId, categoryId));
    }

    /**
     * Remplace l'ensemble des catégories secondaires d'une activité.
     *
     * @throws IllegalArgumentException si {@code categoryIds} contient la
     *         catégorie principale actuelle de l'activité — critère
     *         « relations cohérentes et sans doublons » : une catégorie ne
     *         doit pas être à la fois principale et secondaire pour la
     *         même activité. Les doublons internes à {@code categoryIds}
     *         sont silencieusement dédupliqués (même catégorie listée
     *         deux fois par erreur d'appel), pas rejetés : seule la
     *         redondance avec la catégorie principale est une erreur.
     */
    public void replaceSecondaryCategories(Long activityId, List<Long> categoryIds) {
        Set<Long> deduplicated = new LinkedHashSet<>(categoryIds);
        Optional<Long> primaryCategoryId = primaryCategoryRepository.findByActivityId(activityId)
                .map(ActivityPrimaryCategory::categoryId);
        if (primaryCategoryId.isPresent() && deduplicated.contains(primaryCategoryId.get())) {
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
     * valide, pas seulement temporaire : voir la javadoc de
     * {@link ActivityOrganizerType}). {@code null} supprime le type
     * d'organisateur existant sans le remplacer.
     */
    public void setOrganizerType(Long activityId, Long organizerTypeId) {
        organizerTypeRepository.deleteByActivityId(activityId);
        if (organizerTypeId != null) {
            organizerTypeRepository.save(new ActivityOrganizerType(null, activityId, organizerTypeId));
        }
    }

}
