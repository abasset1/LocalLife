package com.locallife.backend.gdpr.application;

import com.locallife.backend.pro.application.ProNotifier;
import com.locallife.backend.user.application.AccountSelfService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durées de conservation (RGPD, art. 5 ; décision du 22/10/2026, {@code docs/02_Architecture/RGPD.md}), appliquées
 * chaque nuit :
 * <ul>
 *   <li>jetons de réinitialisation, de confirmation d'adresse et de changement d'adresse : 7 jours après leur
 *       expiration ou leur usage ;</li>
 *   <li>comptes jamais confirmés : 30 jours après leur création ;</li>
 *   <li>jetons de notification push sans activité : 6 mois ; historique des alertes envoyées : 6 mois ;</li>
 *   <li>comptes inactifs : avertis par e-mail après 3 ans sans activité, supprimés 30 jours plus tard s'ils ne
 *       sont pas revenus (jamais un administrateur) ;</li>
 *   <li>journaux des comptes et des actions d'administration, demandes de gestion et propositions de modification
 *       closes : 3 ans ;</li>
 *   <li>historique des abonnements terminés : 6 ans (obligations comptables).</li>
 * </ul>
 * Chaque purge est indépendante : l'échec de l'une n'empêche pas les autres.
 */
@Service
public class RetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionService.class);

    static final int INACTIVITY_YEARS = 3;
    static final int INACTIVITY_GRACE_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AccountSelfService accountSelfService;
    private final ProNotifier notifier;

    public RetentionService(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
            AccountSelfService accountSelfService, ProNotifier notifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.accountSelfService = accountSelfService;
        this.notifier = notifier;
    }

    /** Toutes les purges ; renvoie le nombre de lignes traitées par purge (pour les journaux et les tests). */
    @Scheduled(cron = "0 45 3 * * *")
    public Map<String, Integer> applyAll() {
        Map<String, Integer> done = new LinkedHashMap<>();
        run(done, "tokens", this::purgeTokens);
        run(done, "unverifiedAccounts", this::purgeUnverifiedAccounts);
        run(done, "pushTokens", () -> jdbcTemplate.update(
                "DELETE FROM user_push_token WHERE COALESCE(last_seen_at, created_at) < now() - interval '6 months'"));
        run(done, "sentAlerts", () -> jdbcTemplate.update(
                "DELETE FROM activity_alert_sent WHERE sent_at < now() - interval '6 months'")
                + jdbcTemplate.update(
                        "DELETE FROM activity_alert_food_truck_sent WHERE sent_at < now() - interval '6 months'"));
        run(done, "inactivityWarnings", this::warnInactiveAccounts);
        run(done, "inactiveAccounts", this::deleteInactiveAccounts);
        run(done, "auditLogs", this::purgeAuditLogs);
        run(done, "placeRequests", () -> jdbcTemplate.update("""
                DELETE FROM place_claim
                WHERE status IN ('APPROVED', 'REJECTED', 'WITHDRAWN')
                  AND COALESCE(decided_at, updated_at) < now() - interval '3 years'
                """) + jdbcTemplate.update("""
                DELETE FROM place_change_request
                WHERE status <> 'PENDING' AND COALESCE(decided_at, created_at) < now() - interval '3 years'
                """));
        run(done, "subscriptions", () -> jdbcTemplate.update("""
                DELETE FROM subscription
                WHERE status IN ('EXPIRED', 'REJECTED', 'WITHDRAWN', 'REPLACED')
                  AND updated_at < now() - interval '6 years'
                """));
        LOGGER.info("Conservation des données appliquée : {}", done);
        return done;
    }

    private int purgeTokens() {
        String expired = " WHERE expires_at < now() - interval '7 days' "
                + "OR (used AND created_at < now() - interval '7 days')";
        return jdbcTemplate.update("DELETE FROM password_reset_token" + expired)
                + jdbcTemplate.update("DELETE FROM email_verification_token" + expired)
                + jdbcTemplate.update("DELETE FROM email_change_request WHERE expires_at < now() - interval '7 days'");
    }

    /** Comptes jamais confirmés : aucun contenu possible (pas de connexion), supprimés directement. */
    private int purgeUnverifiedAccounts() {
        return jdbcTemplate.update("""
                DELETE FROM users
                WHERE email_verified = FALSE AND role <> 'ADMIN' AND created_at < now() - interval '30 days'
                """);
    }

    /** Avertissement par e-mail, une fois, des comptes sans activité depuis 3 ans. */
    private int warnInactiveAccounts() {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM users
                WHERE role <> 'ADMIN' AND inactivity_warned_at IS NULL
                  AND last_active_at < now() - make_interval(years => ?)
                """, Long.class, INACTIVITY_YEARS);
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE users SET inactivity_warned_at = now() WHERE id = ?", id);
            notifier.notify(id, "Ton compte LocalLife va être supprimé",
                    "Tu ne t'es pas connecté à LocalLife depuis " + INACTIVITY_YEARS + " ans. Sans connexion de ta "
                            + "part d'ici " + INACTIVITY_GRACE_DAYS + " jours, ton compte et tes données seront "
                            + "supprimés (tes activités publiées resteront visibles, sans ton nom). Pour garder ton "
                            + "compte, il suffit de te connecter.", null);
        }
        return ids.size();
    }

    /**
     * Comptes avertis il y a plus de 30 jours et pas revenus depuis : supprimés comme par leur titulaire, chacun
     * dans sa transaction.
     */
    private int deleteInactiveAccounts() {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM users
                WHERE role <> 'ADMIN' AND inactivity_warned_at < now() - make_interval(days => ?)
                  AND last_active_at < inactivity_warned_at
                """, Long.class, INACTIVITY_GRACE_DAYS);
        int deleted = 0;
        for (Long id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> accountSelfService.deleteForInactivity(id));
                deleted++;
            } catch (RuntimeException exception) {
                LOGGER.error("Compte inactif {} non supprimé : {}", id, exception.getClass().getSimpleName());
            }
        }
        return deleted;
    }

    /** Journaux en ajout seul : seule une transaction qui l'annonce peut y supprimer les lignes de plus de 3 ans. */
    private int purgeAuditLogs() {
        Integer purged = transactionTemplate.execute(status -> {
            jdbcTemplate.execute("SET LOCAL locallife.retention = 'on'");
            return jdbcTemplate.update(
                    "DELETE FROM account_audit_event WHERE occurred_at < now() - interval '3 years'")
                    + jdbcTemplate.update(
                            "DELETE FROM admin_action_log WHERE occurred_at < now() - interval '3 years'");
        });
        return purged == null ? 0 : purged;
    }

    private static void run(Map<String, Integer> done, String name, java.util.function.IntSupplier purge) {
        try {
            done.put(name, purge.getAsInt());
        } catch (RuntimeException exception) {
            LOGGER.error("Purge {} en échec : {}", name, exception.getMessage());
            done.put(name, -1);
        }
    }
}
