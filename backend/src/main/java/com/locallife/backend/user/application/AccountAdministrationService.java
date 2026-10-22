package com.locallife.backend.user.application;

import com.locallife.backend.admin.application.AdminActionLog;
import com.locallife.backend.pro.application.ProAccessChanged;
import com.locallife.backend.pro.infrastructure.ProAccountRepository;
import com.locallife.backend.pro.infrastructure.ProAccountRepository.ProRoleSource;
import com.locallife.backend.user.domain.AccountStatus;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.infrastructure.AccountAuditRepository;
import com.locallife.backend.user.infrastructure.AccountAuditRepository.Event;
import com.locallife.backend.user.infrastructure.AccountRepository;
import com.locallife.backend.user.infrastructure.AccountRepository.Account;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gestion des comptes par un administrateur (sprint « Rôles et comptes », ACC-01/02/03, AUD-01). Seule voie
 * applicative de changement de rôle et de statut ; toute tentative, réussie ou refusée, est journalisée dans
 * {@code account_audit_event}.
 *
 * <p>Règles (contrat documenté dans {@code docs/02_Architecture/ROLES_ET_PERMISSIONS.md}) :
 * <ul>
 *   <li>rôles attribuables : {@code USER} et {@code PRO} seulement. {@code ADMIN} ne s'attribue jamais par l'API ;
 *       un compte {@code ADMIN} n'est ni rétrogradé, ni suspendu par l'API : la gestion des administrateurs relève
 *       d'une procédure d'exploitation distincte. Conséquence : l'API ne peut pas faire perdre le dernier accès
 *       administrateur ;</li>
 *   <li>personne n'agit sur son propre compte ;</li>
 *   <li>une suspension exige un motif ; elle ne supprime aucune donnée, et prend effet à la requête suivante du
 *       compte (statut relu en base par {@code JwtFilter}, pas seulement à l'expiration du jeton) ;</li>
 *   <li>le rôle {@code PRO} est un rôle d'accès, distinct de l'abonnement Pro : l'attribuer ou le retirer ne touche
 *       pas à l'abonnement, et aucun abonnement ne donne de droit d'administration.</li>
 * </ul>
 * Codes d'erreur : 400 donnée invalide (rôle inconnu, motif manquant), 403 transition interdite par la politique,
 * 404 compte inconnu, 409 statut déjà dans l'état demandé.
 */
@Service
public class AccountAdministrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAdministrationService.class);

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_REASON_LENGTH = 3;
    public static final int MAX_REASON_LENGTH = 500;

    static final String ROLE_CHANGE = "ROLE_CHANGE";
    static final String SUSPEND = "SUSPEND";
    static final String REACTIVATE = "REACTIVATE";
    static final String LOGOUT_EVERYWHERE = "LOGOUT_EVERYWHERE";
    static final String ADMIN_ACCOUNT_DELETION = "ADMIN_ACCOUNT_DELETION";

    private final AccountRepository accountRepository;
    private final AccountAuditRepository auditRepository;
    private final UserService userService;
    private final ProAccountRepository proAccountRepository;
    private final ApplicationEventPublisher events;
    private final AdminActionLog actionLog;
    private final AccountSelfService accountSelfService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public AccountAdministrationService(
            AccountRepository accountRepository, AccountAuditRepository auditRepository, UserService userService,
            ProAccountRepository proAccountRepository, ApplicationEventPublisher events, AdminActionLog actionLog,
            AccountSelfService accountSelfService, TransactionTemplate transactionTemplate) {
        this(accountRepository, auditRepository, userService, proAccountRepository, events, actionLog,
                accountSelfService, transactionTemplate, Clock.systemUTC());
    }

    AccountAdministrationService(
            AccountRepository accountRepository, AccountAuditRepository auditRepository, UserService userService,
            ProAccountRepository proAccountRepository, ApplicationEventPublisher events, AdminActionLog actionLog,
            AccountSelfService accountSelfService, TransactionTemplate transactionTemplate, Clock clock) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.userService = userService;
        this.proAccountRepository = proAccountRepository;
        this.events = events;
        this.actionLog = actionLog;
        this.accountSelfService = accountSelfService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Auteur d'une action : l'administrateur authentifié. */
    public record Actor(Long userId, String email) {
    }

    /** Page de comptes. */
    public record AccountPage(List<Account> items, long total, int page, int size) {
    }

    /** Page du journal. */
    public record AuditPage(List<Event> items, long total, int page, int size) {
    }

    // --- Consultation ---

    /**
     * @throws AccountActionException 400 si un filtre ou la pagination est invalide
     */
    public AccountPage search(String query, String role, String status, int page, int size) {
        validatePage(page, size);
        if (query != null && query.length() > 255) {
            throw AccountActionException.badRequest("Recherche trop longue (255 caractères au plus).");
        }
        Role roleFilter = parseOptional(Role.class, role, "role", "USER, PRO ou ADMIN");
        AccountStatus statusFilter = parseOptional(AccountStatus.class, status, "status", "ACTIVE ou SUSPENDED");
        long total = accountRepository.count(query, roleFilter, statusFilter);
        List<Account> items = accountRepository.search(query, roleFilter, statusFilter, page * size, size);
        return new AccountPage(items, total, page, size);
    }

    public Optional<Account> find(Long id) {
        return accountRepository.findById(id);
    }

    public AuditPage audit(Long targetUserId, int page, int size) {
        validatePage(page, size);
        return new AuditPage(auditRepository.find(targetUserId, page * size, size),
                auditRepository.count(targetUserId), page, size);
    }

    // --- Rôle ---

    /**
     * Bascule {@code USER} ↔ {@code PRO}, attribution manuelle indépendante de l'offre Pro. Sans effet (et sans
     * erreur) si le compte a déjà ce rôle.
     *
     * @throws AccountActionException 400 rôle inconnu, 403 transition interdite, 404 compte inconnu
     */
    public Account changeRole(Actor actor, Long targetId, String requestedRole) {
        String requested = requestedRole == null ? null : requestedRole.trim().toUpperCase(Locale.ROOT);
        Role role;
        try {
            role = Role.valueOf(requested == null ? "" : requested);
        } catch (IllegalArgumentException exception) {
            throw refuse(actor, targetId, ROLE_CHANGE, null, requested, null,
                    AccountActionException.badRequest("Rôle inconnu : USER ou PRO."));
        }
        Account target = requireTarget(actor, targetId, ROLE_CHANGE, role.name(), null);
        if (role == Role.ADMIN) {
            throw refuse(actor, targetId, ROLE_CHANGE, target.role().name(), role.name(), null,
                    AccountActionException.forbidden("Le rôle ADMIN ne s'attribue pas depuis l'application."));
        }
        refuseOwnOrAdminAccount(actor, target, ROLE_CHANGE, target.role().name(), role.name(), null,
                "Le rôle d'un administrateur ne se modifie pas depuis l'application.");
        if (target.role() == role) {
            return target;
        }
        boolean fromSubscription = proAccountRepository.findRoleState(targetId)
                .filter(state -> state.source() == ProRoleSource.SUBSCRIPTION).isPresent();
        if (role == Role.USER && fromSubscription) {
            throw refuse(actor, targetId, ROLE_CHANGE, target.role().name(), role.name(), null,
                    AccountActionException.conflict("Ce rôle découle d'une offre Pro en cours : retire l'abonnement "
                            + "pour le retirer."));
        }
        userService.changeRole(targetId, role);
        // Rôle PRO attribué à la main : il ne dépend d'aucun abonnement (décision du 22/10/2026). Retiré, il
        // emporte l'accès professionnel, sauf offre Pro en cours.
        proAccountRepository.setRole(targetId, role, role == Role.PRO ? ProRoleSource.ADMIN : null);
        events.publishEvent(new ProAccessChanged(targetId));
        record(actor, targetId, ROLE_CHANGE, target.role().name(), role.name(), null, "SUCCESS", null);
        LOGGER.info("Rôle du compte {} : {} → {} (par le compte {})", targetId, target.role(), role, actor.userId());
        return accountRepository.findById(targetId).orElseThrow();
    }

    // --- Sessions et suppression (22/10/2026) ---

    /** Ferme toutes les sessions d'un compte (compte compromis) : ses jetons émis jusqu'ici sont refusés. */
    public Account logoutEverywhere(Actor actor, Long targetId) {
        Account target = requireTarget(actor, targetId, LOGOUT_EVERYWHERE, null, null);
        refuseOwnOrAdminAccount(actor, target, LOGOUT_EVERYWHERE, null, null, null,
                "Les sessions d'un administrateur ne se ferment pas depuis l'application.");
        accountRepository.revokeTokensIssuedBefore(targetId, now());
        record(actor, targetId, LOGOUT_EVERYWHERE, null, null, null, "SUCCESS", "Toutes les sessions fermées");
        LOGGER.info("Sessions du compte {} fermées par le compte {}", targetId, actor.userId());
        return accountRepository.findById(targetId).orElseThrow();
    }

    /**
     * Supprime un compte comme le ferait son titulaire ({@code AccountSelfService#deleteAccountData}) ; motif
     * obligatoire. Jamais un administrateur ni son propre compte.
     */
    public void deleteAccount(Actor actor, Long targetId, String reason) {
        String cleanReason = reason == null ? null : reason.trim();
        Account target = requireTarget(actor, targetId, ADMIN_ACCOUNT_DELETION, null, cleanReason);
        if (cleanReason == null || cleanReason.length() < MIN_REASON_LENGTH
                || cleanReason.length() > MAX_REASON_LENGTH) {
            throw refuse(actor, targetId, ADMIN_ACCOUNT_DELETION, null, null, cleanReason,
                    AccountActionException.badRequest("Motif de suppression obligatoire (" + MIN_REASON_LENGTH
                            + " à " + MAX_REASON_LENGTH + " caractères)."));
        }
        refuseOwnOrAdminAccount(actor, target, ADMIN_ACCOUNT_DELETION, null, null, cleanReason,
                "Un compte administrateur ne se supprime pas depuis l'application.");
        // Les refus ci-dessus restent tracés ; la suppression elle-même est tout ou rien.
        transactionTemplate.executeWithoutResult(status -> {
            int deletedContributions = accountSelfService.deleteAccountData(targetId);
            record(actor, targetId, ADMIN_ACCOUNT_DELETION, null, null, cleanReason, "SUCCESS",
                    deletedContributions + " contribution(s) non publiée(s) supprimée(s)");
            accountRepository.delete(targetId);
        });
        LOGGER.info("Compte {} supprimé par le compte {}", targetId, actor.userId());
    }

    // --- Statut ---

    /**
     * @throws AccountActionException 400 motif manquant, 403 compte administrateur ou propre compte, 404 compte
     *         inconnu, 409 déjà suspendu
     */
    public Account suspend(Actor actor, Long targetId, String reason) {
        String cleanReason = reason == null ? null : reason.trim();
        Account target = requireTarget(actor, targetId, SUSPEND, AccountStatus.SUSPENDED.name(), cleanReason);
        if (cleanReason == null || cleanReason.length() < MIN_REASON_LENGTH
                || cleanReason.length() > MAX_REASON_LENGTH) {
            throw refuse(actor, targetId, SUSPEND, target.status().name(), AccountStatus.SUSPENDED.name(),
                    cleanReason, AccountActionException.badRequest("Motif de suspension obligatoire ("
                            + MIN_REASON_LENGTH + " à " + MAX_REASON_LENGTH + " caractères)."));
        }
        refuseOwnOrAdminAccount(actor, target, SUSPEND, target.status().name(), AccountStatus.SUSPENDED.name(),
                cleanReason, "Un compte administrateur ne se suspend pas depuis l'application.");
        if (target.status() == AccountStatus.SUSPENDED) {
            throw refuse(actor, targetId, SUSPEND, target.status().name(), AccountStatus.SUSPENDED.name(),
                    cleanReason, AccountActionException.conflict("Ce compte est déjà suspendu."));
        }
        accountRepository.updateStatus(targetId, AccountStatus.SUSPENDED, cleanReason, actor.userId(), now());
        record(actor, targetId, SUSPEND, target.status().name(), AccountStatus.SUSPENDED.name(), cleanReason,
                "SUCCESS", null);
        LOGGER.info("Compte {} suspendu par le compte {}", targetId, actor.userId());
        return accountRepository.findById(targetId).orElseThrow();
    }

    /**
     * @param reason motif facultatif, conservé dans le journal
     * @throws AccountActionException 403 propre compte, 404 compte inconnu, 409 compte déjà actif
     */
    public Account reactivate(Actor actor, Long targetId, String reason) {
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw refuse(actor, targetId, REACTIVATE, null, AccountStatus.ACTIVE.name(), null,
                    AccountActionException.badRequest("Motif trop long (" + MAX_REASON_LENGTH
                            + " caractères au plus)."));
        }
        Account target = requireTarget(actor, targetId, REACTIVATE, AccountStatus.ACTIVE.name(), cleanReason);
        if (actor.userId() != null && actor.userId().equals(targetId)) {
            throw refuse(actor, targetId, REACTIVATE, target.status().name(), AccountStatus.ACTIVE.name(),
                    cleanReason, AccountActionException.forbidden("Personne n'agit sur son propre compte."));
        }
        if (target.status() == AccountStatus.ACTIVE) {
            throw refuse(actor, targetId, REACTIVATE, target.status().name(), AccountStatus.ACTIVE.name(),
                    cleanReason, AccountActionException.conflict("Ce compte est déjà actif."));
        }
        accountRepository.updateStatus(targetId, AccountStatus.ACTIVE, cleanReason, actor.userId(), now());
        record(actor, targetId, REACTIVATE, target.status().name(), AccountStatus.ACTIVE.name(), cleanReason,
                "SUCCESS", null);
        LOGGER.info("Compte {} réactivé par le compte {}", targetId, actor.userId());
        return accountRepository.findById(targetId).orElseThrow();
    }

    // --- Règles communes ---

    private Account requireTarget(Actor actor, Long targetId, String action, String newValue, String reason) {
        return accountRepository.findById(targetId).orElseThrow(() -> refuse(actor, targetId, action, null,
                newValue, reason, AccountActionException.notFound("Compte introuvable.")));
    }

    private void refuseOwnOrAdminAccount(
            Actor actor, Account target, String action, String oldValue, String newValue, String reason,
            String adminMessage) {
        if (actor.userId() != null && actor.userId().equals(target.id())) {
            throw refuse(actor, target.id(), action, oldValue, newValue, reason,
                    AccountActionException.forbidden("Personne n'agit sur son propre compte."));
        }
        if (target.role() == Role.ADMIN) {
            throw refuse(actor, target.id(), action, oldValue, newValue, reason,
                    AccountActionException.forbidden(adminMessage));
        }
    }

    private AccountActionException refuse(
            Actor actor, Long targetId, String action, String oldValue, String newValue, String reason,
            AccountActionException exception) {
        record(actor, targetId, action, oldValue, newValue, reason, "REFUSED", exception.getMessage());
        return exception;
    }

    private void record(
            Actor actor, Long targetId, String action, String oldValue, String newValue, String reason,
            String outcome, String detail) {
        auditRepository.append(new Event(null, now(), actor.userId(), actor.email(), targetId, action, oldValue,
                newValue, reason, outcome, detail));
        if ("SUCCESS".equals(outcome)) {
            Map<String, Object> before = new HashMap<>();
            before.put("value", oldValue);
            Map<String, Object> after = new HashMap<>();
            after.put("value", newValue);
            actionLog.record(actor.userId(), "ACCOUNT_" + action, "ACCOUNT", targetId, detail, before, after, reason);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw AccountActionException.badRequest("Le paramètre 'page' doit être positif ou nul.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw AccountActionException.badRequest(
                    "Le paramètre 'size' doit être compris entre 1 et " + MAX_PAGE_SIZE + ".");
        }
    }

    private static <E extends Enum<E>> E parseOptional(Class<E> type, String value, String name, String expected) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AccountActionException.badRequest("Paramètre '" + name + "' invalide : " + expected + ".");
        }
    }
}
