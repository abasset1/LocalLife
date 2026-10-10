package com.locallife.backend.activity.application;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion de ses contributions par leur auteur : liste, modification et suppression. Mêmes règles d'accès
 * que {@link ActivityService#cancel} : réservé au créateur de l'activité ou à un administrateur, jamais
 * pour une activité importée (sans créateur).
 *
 * <p>Une modification par le contributeur repasse l'activité en {@code PENDING} : elle quitte la carte
 * jusqu'à ce qu'un administrateur la valide de nouveau. Un administrateur, lui, modifie sans changer le
 * statut (il est le validateur).
 */
@Service
public class ContributionService {

    static final String PENDING_STATUS = "PENDING";

    /** Statuts modifiables : une activité refusée peut être corrigée puis resoumise. */
    private static final Set<String> EDITABLE_STATUSES = Set.of(PENDING_STATUS, "PUBLISHED", "REJECTED");

    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final RecurringContributionService recurringContributionService;
    private final ActivityClassificationService activityClassificationService;
    private final ActivityMediaService activityMediaService;
    private final ActivityLinkService activityLinkService;
    private final AccessibilityService accessibilityService;
    private final ActivityTagService activityTagService;
    private final ActivityMetadataService activityMetadataService;

    public ContributionService(
            ActivityRepository activityRepository, ActivityService activityService,
            RecurringContributionService recurringContributionService,
            ActivityClassificationService activityClassificationService, ActivityMediaService activityMediaService,
            ActivityLinkService activityLinkService, AccessibilityService accessibilityService,
            ActivityTagService activityTagService, ActivityMetadataService activityMetadataService) {
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.recurringContributionService = recurringContributionService;
        this.activityClassificationService = activityClassificationService;
        this.activityMediaService = activityMediaService;
        this.activityLinkService = activityLinkService;
        this.accessibilityService = accessibilityService;
        this.activityTagService = activityTagService;
        this.activityMetadataService = activityMetadataService;
    }

    /** Contributions de l'utilisateur, tous statuts confondus, les plus récentes d'abord. */
    public List<Activity> findByCreator(Long userId) {
        return activityRepository.findByCreatedByOrderByIdDesc(userId);
    }

    /**
     * Contribution que le demandeur peut gérer.
     *
     * @return vide si aucune activité ne correspond à {@code id}
     * @throws IllegalArgumentException si l'activité n'a pas de créateur (activité importée)
     * @throws ActivityAccessDeniedException si le demandeur n'est ni son créateur ni un administrateur
     */
    public Optional<Activity> findManageable(Long id, Long requesterId, boolean requesterIsAdmin) {
        Optional<Activity> activity = activityRepository.findById(id);
        activity.ifPresent(found -> checkAccess(found, requesterId, requesterIsAdmin, "gérer"));
        return activity;
    }

    /**
     * Modifie une contribution (titre, description, catégories, adresse, dates ou récurrence). Les
     * liens/contacts sont remplacés par l'appelant. Sans récurrence, l'ancienne est supprimée.
     *
     * @return l'activité modifiée, vide si aucune activité ne correspond à {@code id}
     * @throws IllegalArgumentException si un champ est invalide, si l'activité est importée ou si son
     *     statut n'est pas modifiable (annulée, archivée)
     * @throws ActivityAccessDeniedException si le demandeur n'est ni son créateur ni un administrateur
     */
    @Transactional
    public Optional<Activity> update(
            Long id, Long requesterId, boolean requesterIsAdmin, String title, String description,
            String category, String address, String startDate, String endDate, List<Long> categoryIds,
            ContributionRecurrence recurrence) {
        Optional<Activity> found = activityRepository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Activity existing = found.get();
        checkAccess(existing, requesterId, requesterIsAdmin, "modifier");
        if (!EDITABLE_STATUSES.contains(existing.status())) {
            throw new IllegalArgumentException("L'activité " + id + " ne peut plus être modifiée (statut : "
                    + existing.status() + ").");
        }

        List<LocalDate> dates = null;
        String resolvedStartDate = startDate;
        String resolvedEndDate = endDate;
        if (recurrence != null) {
            dates = recurringContributionService.planDates(recurrence);
            resolvedStartDate = RecurringContributionService.firstStart(recurrence, dates).toString();
            resolvedEndDate = RecurringContributionService.lastEnd(recurrence, dates).toString();
        }
        String status = requesterIsAdmin ? existing.status() : PENDING_STATUS;
        Activity updated = activityService.updateContribution(
                existing, title, description, category, address, resolvedStartDate, resolvedEndDate, status,
                categoryIds);

        recurringContributionService.clearSchedules(id);
        if (recurrence != null) {
            recurringContributionService.attachSchedule(updated, recurrence, dates);
        }
        return Optional.of(updated);
    }

    /**
     * Supprime définitivement une contribution et tout ce qui lui est rattaché (horaires, liens, médias,
     * classification ; favoris et alertes partent en cascade). Les lieux et médias, partageables, restent.
     *
     * @return {@code false} si aucune activité ne correspond à {@code id}
     * @throws IllegalArgumentException si l'activité est importée
     * @throws ActivityAccessDeniedException si le demandeur n'est ni son créateur ni un administrateur
     */
    @Transactional
    public boolean delete(Long id, Long requesterId, boolean requesterIsAdmin) {
        Optional<Activity> found = activityRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        checkAccess(found.get(), requesterId, requesterIsAdmin, "supprimer");

        recurringContributionService.clearSchedules(id);
        activityMediaService.deleteByActivityId(id);
        activityLinkService.deleteByActivityId(id);
        accessibilityService.deleteByActivityId(id);
        activityTagService.deleteByActivityId(id);
        activityMetadataService.deleteByActivityId(id);
        activityClassificationService.replaceActivityTypes(id, List.of());
        activityClassificationService.replaceAudiences(id, List.of());
        activityClassificationService.setOrganizerType(id, null);
        activityRepository.deleteById(id);
        return true;
    }

    private static void checkAccess(Activity activity, Long requesterId, boolean requesterIsAdmin, String action) {
        if (activity.createdBy() == null) {
            throw new IllegalArgumentException("L'activité " + activity.id()
                    + " a été importée depuis une source externe : impossible de la " + action + " ici.");
        }
        if (!requesterIsAdmin && !activity.createdBy().equals(requesterId)) {
            throw new ActivityAccessDeniedException(
                    "Seul le créateur de l'activité " + activity.id() + " ou un administrateur peut la "
                            + action + ".");
        }
    }
}
