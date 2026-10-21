package com.locallife.backend.subscription.application;

import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.subscription.domain.Entitlements;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.subscription.domain.Subscription;
import com.locallife.backend.subscription.domain.SubscriptionStatus;
import com.locallife.backend.subscription.infrastructure.SubscriptionRepository;
import com.locallife.backend.subscription.infrastructure.SubscriptionRepository.Change;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abonnements Premium et Pro sans paiement réel (BILLING-01, ADR-0014, décisions du 19/10/2026).
 *
 * <p>L'utilisateur souscrit une offre, activée tout de suite comme si le paiement était validé (aucun prestataire
 * branché ; Stripe plus tard), ou résilie son abonnement. Un administrateur peut retirer un accès, et traite encore
 * les demandes en attente créées avant la souscription directe. À l'activation par un administrateur, une durée
 * facultative fixe la fin de la période ; sans durée, l'abonnement court jusqu'à résiliation. Une résiliation garde
 * l'accès jusqu'à la fin de la période en cours (immédiate s'il n'y en a pas) ; l'expiration est appliquée au moment
 * prévu. Chaque changement est journalisé ({@code subscription_event}). La consultation de la carte et de la liste
 * reste gratuite quoi qu'il arrive.
 */
@Service
public class SubscriptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final int HISTORY_SIZE = 10;
    private static final int MAX_REASON_LENGTH = 2000;
    /** Abonnements activés sans prestataire de paiement, à distinguer des activations par un administrateur. */
    private static final String SIMULATED_PROVIDER = "SIMULATED";

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    /** Situation d'un utilisateur : offre effective, abonnement en cours, demande en attente, historique. */
    public record Overview(Plan plan, Subscription current, Subscription pending, List<Subscription> history) {
    }

    public Overview overview(Long userId) {
        Subscription current = repository.findCurrent(userId).orElse(null);
        return new Overview(Entitlements.effectivePlan(current, OffsetDateTime.now()), current,
                repository.findRequested(userId).orElse(null), repository.findHistory(userId, HISTORY_SIZE));
    }

    /** Offre effective, pour les contrôles d'accès ({@code ProAccessPolicy}). */
    public Plan effectivePlan(Long userId) {
        return Entitlements.effectivePlan(repository.findCurrent(userId).orElse(null), OffsetDateTime.now());
    }

    // ---------------------------------------------------------------- utilisateur

    /**
     * Souscription, tant qu'aucun prestataire n'est branché (Stripe plus tard) :
     * <ul>
     *   <li><b>Premium</b> : le paiement est réputé validé et l'offre est activée tout de suite, sans échéance
     *       (décision du 19/10/2026). L'abonnement en cours éventuel est remplacé ;</li>
     *   <li><b>Pro</b> : simple demande, activée par l'équipe ({@link #approve}) — décision du 21/10/2026, retour à
     *       B1 pour Pro : sans paiement réel, l'activation immédiate donnait à n'importe quel compte l'espace
     *       professionnel et la publication de food trucks sans contrôle. L'abonnement en cours reste actif
     *       jusqu'à l'approbation.</li>
     * </ul>
     * Une ancienne demande en attente est retirée.
     */
    @Transactional
    public Overview subscribe(Long userId, Plan plan) {
        if (plan == null || plan == Plan.FREE) {
            throw ProActionException.invalid("Offre inconnue : PREMIUM ou PRO.");
        }
        Optional<Subscription> current = repository.findCurrent(userId);
        if (current.filter(found -> found.plan() == plan && found.status() == SubscriptionStatus.ACTIVE)
                .isPresent()) {
            throw ProActionException.conflict("Tu as déjà cette offre.");
        }
        Optional<Subscription> pending = repository.findRequested(userId);
        if (pending.isPresent()) {
            apply(pending.get(), SubscriptionStatus.WITHDRAWN, Change.none(), userId, "Remplacée par une souscription");
        }
        Long id;
        try {
            id = repository.insertRequest(userId, plan, SIMULATED_PROVIDER);
        } catch (DuplicateKeyException exception) {
            throw ProActionException.conflict("L'abonnement a changé entre-temps, recharge la page.");
        }
        repository.insertEvent(id, userId, null, SubscriptionStatus.REQUESTED, null);
        if (plan == Plan.PRO) {
            LOGGER.info("Abonnement {} : utilisateur {}, demande d'offre Pro en attente de validation", id, userId);
            return overview(userId);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (current.isPresent()) {
            apply(current.get(), SubscriptionStatus.REPLACED, new Change(false, null, true, now, null, null, null),
                    userId, "Remplacé par l'offre " + plan);
        }
        apply(repository.findById(id).orElseThrow(), SubscriptionStatus.ACTIVE,
                new Change(true, now, true, null, false, null, null), userId, "Paiement simulé");
        LOGGER.info("Abonnement {} : utilisateur {}, offre {} activée (paiement simulé)", id, userId, plan);
        return overview(userId);
    }

    @Transactional
    public Overview withdraw(Long userId) {
        Subscription pending = repository.findRequested(userId)
                .orElseThrow(() -> ProActionException.notFound("Aucune demande en attente."));
        apply(pending, SubscriptionStatus.WITHDRAWN, Change.none(), userId, null);
        return overview(userId);
    }

    /**
     * Résiliation par l'utilisateur : l'accès dure jusqu'à la fin de la période en cours ; sans période (activation
     * sans durée), il s'arrête tout de suite.
     */
    @Transactional
    public Overview cancel(Long userId) {
        Subscription current = repository.findCurrent(userId)
                .filter(subscription -> subscription.status() == SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> ProActionException.notFound("Aucun abonnement à résilier."));
        OffsetDateTime now = OffsetDateTime.now();
        boolean hasPeriod = current.currentPeriodEnd() != null && current.currentPeriodEnd().isAfter(now);
        Change change = hasPeriod
                ? new Change(false, null, false, null, true, null, null)
                : new Change(false, null, true, now, false, null, null);
        apply(current, hasPeriod ? SubscriptionStatus.CANCELED : SubscriptionStatus.EXPIRED, change, userId, null);
        return overview(userId);
    }

    // ---------------------------------------------------------------- administration

    public List<Subscription> findByStatuses(Collection<SubscriptionStatus> statuses, int limit, int offset) {
        return repository.findByStatuses(statuses, limit, offset);
    }

    /**
     * Active une demande ; {@code endsOn} : dernier jour d'accès (inclus), ou {@code null} pour un abonnement sans
     * échéance. L'abonnement en cours éventuel (autre offre) est remplacé.
     */
    @Transactional
    public Subscription approve(Long adminId, Long id, LocalDate endsOn) {
        Subscription request = requested(id);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime end = endsOn == null ? null : endsOn.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
        if (end != null && !end.isAfter(now)) {
            throw ProActionException.invalid("La date de fin doit être aujourd'hui ou plus tard.");
        }
        Optional<Subscription> current = repository.findCurrent(request.userId());
        if (current.isPresent()) {
            apply(current.get(), SubscriptionStatus.REPLACED, new Change(false, null, true, now, null, adminId, null),
                    adminId, "Remplacé par l'offre " + request.plan());
        }
        apply(request, SubscriptionStatus.ACTIVE, new Change(true, now, true, end, false, adminId, null), adminId,
                null);
        LOGGER.info("Abonnement {} ({}) activé par l'administrateur {} jusqu'au {}", id, request.plan(), adminId,
                endsOn == null ? "résiliation" : endsOn);
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Subscription reject(Long adminId, Long id, String reason) {
        Subscription request = requested(id);
        String cleanReason = reason(reason);
        if (cleanReason == null) {
            throw ProActionException.invalid("Un motif est obligatoire pour refuser une demande.");
        }
        apply(request, SubscriptionStatus.REJECTED, new Change(false, null, false, null, null, adminId, cleanReason),
                adminId, cleanReason);
        return repository.findById(id).orElseThrow();
    }

    /** Retire immédiatement l'accès d'un abonnement en cours. */
    @Transactional
    public Subscription revoke(Long adminId, Long id, String reason) {
        Subscription subscription = repository.findById(id)
                .filter(found -> found.status() == SubscriptionStatus.ACTIVE
                        || found.status() == SubscriptionStatus.CANCELED)
                .orElseThrow(() -> ProActionException.notFound("Aucun abonnement en cours avec cet identifiant."));
        String cleanReason = reason(reason);
        apply(subscription, SubscriptionStatus.EXPIRED,
                new Change(false, null, true, OffsetDateTime.now(), null, adminId, cleanReason), adminId, cleanReason);
        LOGGER.info("Abonnement {} retiré par l'administrateur {}", id, adminId);
        return repository.findById(id).orElseThrow();
    }

    /**
     * Suppression du compte (décision du 21/10/2026) : la demande en attente est retirée et l'abonnement en cours
     * prend fin immédiatement, avant que le compte ne disparaisse. L'historique reste en base, détaché du compte
     * ({@code subscription.user_id} passe à {@code NULL}, V70).
     */
    @Transactional
    public void closeForDeletedAccount(Long userId) {
        String reason = "Compte supprimé par son titulaire";
        repository.findRequested(userId).ifPresent(pending ->
                apply(pending, SubscriptionStatus.WITHDRAWN, Change.none(), userId, reason));
        repository.findCurrent(userId).ifPresent(current -> apply(current, SubscriptionStatus.EXPIRED,
                new Change(false, null, true, OffsetDateTime.now(), null, null, reason), userId, reason));
    }

    /**
     * Expire, chaque heure, les abonnements dont la période est terminée. Les droits s'arrêtaient déjà à l'échéance.
     */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void expireDue() {
        for (Subscription due : repository.findDue(OffsetDateTime.now())) {
            if (repository.transition(due.id(), due.status(), SubscriptionStatus.EXPIRED, Change.none())) {
                repository.insertEvent(due.id(), null, due.status(), SubscriptionStatus.EXPIRED, "Période terminée");
            }
        }
    }

    // ---------------------------------------------------------------- outils

    private Subscription requested(Long id) {
        return repository.findById(id).filter(found -> found.status() == SubscriptionStatus.REQUESTED)
                .orElseThrow(() -> ProActionException.notFound("Aucune demande en attente avec cet identifiant."));
    }

    private void apply(Subscription subscription, SubscriptionStatus to, Change change, Long actorId, String reason) {
        if (!repository.transition(subscription.id(), subscription.status(), to, change)) {
            throw ProActionException.conflict("L'abonnement a changé entre-temps, recharge la page.");
        }
        repository.insertEvent(subscription.id(), actorId, subscription.status(), to, reason);
    }

    private static String reason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw ProActionException.invalid("Le motif ne doit pas dépasser " + MAX_REASON_LENGTH + " caractères.");
        }
        return trimmed;
    }
}
