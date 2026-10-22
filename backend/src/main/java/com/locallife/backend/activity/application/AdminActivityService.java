package com.locallife.backend.activity.application;

import com.locallife.backend.activity.application.ActivityBulkDeletionService.Criteria;
import com.locallife.backend.activity.application.ActivityBulkDeletionService.DeletionCountChangedException;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activity.infrastructure.ActivityTrashRepository;
import com.locallife.backend.activity.infrastructure.ActivityTrashRepository.TrashedActivity;
import com.locallife.backend.admin.application.AdminActionLog;
import com.locallife.backend.pro.application.ProNotifier;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actions d'un administrateur sur les activités (décisions du 22/10/2026, {@code ROLES_ET_PERMISSIONS.md}
 * § « Administrateur ») :
 * <ul>
 *   <li>publier ou refuser une activité en attente, jamais la sienne ; un refus exige un motif, envoyé à
 *       l'auteur ;</li>
 *   <li>annuler ou supprimer la contribution d'un autre : motif obligatoire, auteur prévenu par e-mail ;</li>
 *   <li>suppression = <b>corbeille</b> : l'activité disparaît partout, reste restaurable {@value #TRASH_DAYS} jours,
 *       puis elle est purgée définitivement ; une suppression en masse se restaure d'un coup ;</li>
 *   <li>tout est inscrit au journal des actions d'administration ({@link AdminActionLog}).</li>
 * </ul>
 */
@Service
public class AdminActivityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminActivityService.class);

    static final int TRASH_DAYS = 30;
    static final int MAX_REASON_LENGTH = 2000;
    static final String TARGET = "ACTIVITY";

    private final ActivityService activityService;
    private final ActivityRepository activityRepository;
    private final ActivityTrashRepository trashRepository;
    private final ActivityBulkDeletionService bulkDeletionService;
    private final AdminActionLog actionLog;
    private final ProNotifier notifier;

    public AdminActivityService(ActivityService activityService, ActivityRepository activityRepository,
            ActivityTrashRepository trashRepository, ActivityBulkDeletionService bulkDeletionService,
            AdminActionLog actionLog, ProNotifier notifier) {
        this.activityService = activityService;
        this.activityRepository = activityRepository;
        this.trashRepository = trashRepository;
        this.bulkDeletionService = bulkDeletionService;
        this.actionLog = actionLog;
        this.notifier = notifier;
    }

    /** Page de la corbeille. */
    public record TrashPage(List<TrashedActivity> items, long total, int page, int size) {
    }

    // ---------------------------------------------------------------- modération

    @Transactional
    public Optional<Activity> publish(Long adminId, Long id) {
        Optional<Activity> existing = activityRepository.findById(id);
        existing.ifPresent(activity -> refuseOwn(adminId, activity, "publier"));
        Optional<Activity> published = activityService.publish(id);
        published.ifPresent(activity -> {
            trashRepository.setModerationReason(id, null);
            actionLog.record(adminId, "ACTIVITY_PUBLISH", TARGET, id, activity.title(),
                    Map.of("status", existing.get().status()), Map.of("status", activity.status()), null);
        });
        return published;
    }

    @Transactional
    public Optional<Activity> reject(Long adminId, Long id, String reason) {
        String cleanReason = requiredReason(reason, "refuser une activité");
        Optional<Activity> existing = activityRepository.findById(id);
        existing.ifPresent(activity -> refuseOwn(adminId, activity, "refuser"));
        Optional<Activity> rejected = activityService.reject(id);
        rejected.ifPresent(activity -> {
            trashRepository.setModerationReason(id, cleanReason);
            actionLog.record(adminId, "ACTIVITY_REJECT", TARGET, id, activity.title(),
                    Map.of("status", existing.get().status()), Map.of("status", activity.status()), cleanReason);
            notifyAuthor(activity, "Ta proposition « " + activity.title() + " » n'a pas été publiée",
                    "L'équipe LocalLife n'a pas publié ta proposition « " + activity.title()
                            + " ». Tu peux la corriger depuis « Mes contributions » : elle repartira en validation.",
                    cleanReason);
        });
        return rejected;
    }

    /** Annulation par un administrateur de la contribution d'un autre : motif obligatoire, auteur prévenu. */
    @Transactional
    public Optional<Activity> cancelForOther(Long adminId, Long id, String reason) {
        String cleanReason = requiredReason(reason, "annuler la contribution d'un autre");
        Optional<Activity> existing = activityRepository.findById(id);
        Optional<Activity> cancelled = activityService.cancel(id, adminId, true);
        cancelled.ifPresent(activity -> {
            trashRepository.setModerationReason(id, cleanReason);
            actionLog.record(adminId, "ACTIVITY_CANCEL", TARGET, id, activity.title(),
                    Map.of("status", existing.get().status()), Map.of("status", activity.status()), cleanReason);
            notifyAuthor(activity, "Ton activité « " + activity.title() + " » a été annulée",
                    "L'équipe LocalLife a annulé ton activité « " + activity.title() + " ».", cleanReason);
        });
        return cancelled;
    }

    /** Modification par un administrateur de la contribution d'un autre : inscrite au journal (avant/après). */
    public void recordEditForOther(Long adminId, Activity before, Activity after) {
        actionLog.record(adminId, "ACTIVITY_EDIT", TARGET, after.id(), after.title(), snapshot(before),
                snapshot(after), null);
    }

    // ---------------------------------------------------------------- corbeille

    /** Met une activité à la corbeille (importée ou non). {@code false} si elle n'existe pas ou y est déjà. */
    @Transactional
    public boolean trashOne(Long adminId, Long id, String reason) {
        String cleanReason = requiredReason(reason, "supprimer une activité");
        Optional<Activity> existing = activityRepository.findById(id);
        if (existing.isEmpty() || !trashRepository.trash(List.of(id), adminId, cleanReason, null).contains(id)) {
            return false;
        }
        Activity activity = existing.get();
        actionLog.record(adminId, "ACTIVITY_TRASH", TARGET, id, activity.title(),
                Map.of("status", activity.status()), Map.of("status", "DELETED"), cleanReason);
        if (!Objects.equals(activity.createdBy(), adminId)) {
            notifyAuthor(activity, "Ton activité « " + activity.title() + " » a été supprimée",
                    "L'équipe LocalLife a retiré ton activité « " + activity.title() + " ».", cleanReason);
        }
        return true;
    }

    /**
     * Suppression en masse : même aperçu et même contrôle du nombre qu'avant ({@code expectedCount}), mais vers la
     * corbeille, avec un identifiant de lot pour tout restaurer d'un coup. Les auteurs des contributions visées sont
     * prévenus.
     *
     * @return identifiant du lot et nombre d'activités mises à la corbeille
     */
    @Transactional
    public BulkResult trashMany(Long adminId, Criteria criteria, long expectedCount, String reason) {
        String cleanReason = requiredReason(reason, "supprimer des activités");
        List<Long> ids = bulkDeletionService.matchingIds(criteria);
        if (ids.size() != expectedCount) {
            throw new DeletionCountChangedException(expectedCount, ids.size());
        }
        UUID batch = UUID.randomUUID();
        List<Long> trashed = trashRepository.trash(ids, adminId, cleanReason, batch);
        Map<String, Object> criteriaLog = new LinkedHashMap<>();
        criteriaLog.put("sourceId", criteria.sourceId());
        criteriaLog.put("status", criteria.status());
        criteriaLog.put("startFrom", criteria.startFrom() == null ? null : criteria.startFrom().toString());
        criteriaLog.put("startTo", criteria.startTo() == null ? null : criteria.startTo().toString());
        criteriaLog.put("all", criteria.all());
        actionLog.record(adminId, "ACTIVITY_TRASH_BULK", "ACTIVITY_BATCH", batch,
                trashed.size() + " activité(s) mises à la corbeille", criteriaLog,
                Map.of("count", trashed.size(), "batch", batch.toString()), cleanReason);
        for (Long id : trashed) {
            Activity activity = activityRepository.findById(id).orElse(null);
            if (activity != null && activity.createdBy() != null && !activity.createdBy().equals(adminId)) {
                notifyAuthor(activity, "Ton activité « " + activity.title() + " » a été supprimée",
                        "L'équipe LocalLife a retiré ton activité « " + activity.title() + " ».", cleanReason);
            }
        }
        return new BulkResult(batch, trashed.size());
    }

    /** Résultat d'une suppression en masse. */
    public record BulkResult(UUID batch, int count) {
    }

    @Transactional
    public boolean restore(Long adminId, Long id) {
        Optional<TrashedActivity> trashed = trashRepository.findTrashed(id);
        if (trashed.isEmpty() || trashRepository.restore(List.of(id)).isEmpty()) {
            return false;
        }
        actionLog.record(adminId, "ACTIVITY_RESTORE", TARGET, id, trashed.get().title(), Map.of("status", "DELETED"),
                Map.of("status", trashed.get().previousStatus()), null);
        return true;
    }

    /** Restaure tout un lot de suppression ; renvoie le nombre d'activités restaurées. */
    @Transactional
    public int restoreBatch(Long adminId, UUID batch) {
        List<Long> restored = trashRepository.restore(trashRepository.idsInBatch(batch));
        if (!restored.isEmpty()) {
            actionLog.record(adminId, "ACTIVITY_RESTORE_BULK", "ACTIVITY_BATCH", batch,
                    restored.size() + " activité(s) restaurée(s)", null, Map.of("count", restored.size()), null);
        }
        return restored.size();
    }

    /** Motif d'un refus ou d'une annulation par l'équipe, montré à l'auteur. */
    public String moderationReason(Long activityId) {
        return trashRepository.moderationReason(activityId).orElse(null);
    }

    public TrashPage trash(int page, int size) {
        return new TrashPage(trashRepository.findTrash(page * size, size), trashRepository.countTrash(), page, size);
    }

    /** Purge, chaque nuit, les activités à la corbeille depuis plus de {@value #TRASH_DAYS} jours. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public int purgeExpired() {
        List<Long> expired = trashRepository.expired(OffsetDateTime.now().minusDays(TRASH_DAYS));
        if (expired.isEmpty()) {
            return 0;
        }
        int purged = bulkDeletionService.deleteDefinitively(expired);
        actionLog.record(null, "ACTIVITY_PURGE", "ACTIVITY_BATCH", null,
                purged + " activité(s) purgée(s) après " + TRASH_DAYS + " jours de corbeille", null,
                Map.of("count", purged), null);
        LOGGER.info("{} activité(s) purgée(s) de la corbeille", purged);
        return purged;
    }

    // ---------------------------------------------------------------- outils

    private void notifyAuthor(Activity activity, String subject, String text, String reason) {
        if (activity.createdBy() != null) {
            notifier.notify(activity.createdBy(), subject, text, reason);
        }
    }

    private static void refuseOwn(Long adminId, Activity activity, String action) {
        if (adminId != null && adminId.equals(activity.createdBy())) {
            throw new ActivityAccessDeniedException("Un administrateur ne peut pas " + action
                    + " sa propre activité : un autre administrateur s'en charge.");
        }
    }

    static String requiredReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire pour " + action + ".");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Le motif ne doit pas dépasser " + MAX_REASON_LENGTH + " caractères.");
        }
        return trimmed;
    }

    private static Map<String, Object> snapshot(Activity activity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", activity.title());
        values.put("description", activity.description());
        values.put("address", activity.address());
        values.put("city", activity.city());
        values.put("startDate", activity.startDate() == null ? null : activity.startDate().toString());
        values.put("endDate", activity.endDate() == null ? null : activity.endDate().toString());
        values.put("status", activity.status());
        return values;
    }
}
