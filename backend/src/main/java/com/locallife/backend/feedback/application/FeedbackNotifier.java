package com.locallife.backend.feedback.application;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.feedback.application.FeedbackService.FeedbackView;
import com.locallife.backend.feedback.application.FeedbackService.Kind;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Prévient les administrateurs (comptes actifs au rôle ADMIN) par e-mail de chaque nouvelle suggestion ou anomalie,
 * avec le message et un lien vers Administration → Retours. Envoyé après la validation de la transaction ; un échec
 * d'envoi est journalisé sans perdre le message, qui reste consultable dans l'administration.
 */
@Service
public class FeedbackNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackNotifier.class);

    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;
    private final String frontendBaseUrl;

    public FeedbackNotifier(EmailService emailService, JdbcTemplate jdbcTemplate,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void notifyAdmins(FeedbackView feedback) {
        List<String> recipients = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE role = 'ADMIN' AND account_status = 'ACTIVE' ORDER BY id", String.class);
        if (recipients.isEmpty()) {
            return;
        }
        String label = feedback.kind() == Kind.BUG ? "Anomalie signalée" : "Nouvelle suggestion";
        String subject = "Neiri — " + label + " par " + feedback.username();
        String body = "Bonjour,\n\n"
                + feedback.username() + " (" + feedback.email() + ") a envoyé "
                + (feedback.kind() == Kind.BUG ? "un signalement d'anomalie" : "une suggestion") + " :\n\n"
                + feedback.message() + "\n\n"
                + (feedback.pageUrl() == null ? "" : "Page : " + feedback.pageUrl() + "\n")
                + "À consulter dans l'administration : " + frontendBaseUrl + "/admin?tab=feedback\n\n"
                + "L'équipe Neiri";
        Runnable send = () -> recipients.forEach(recipient -> {
            try {
                emailService.sendAdminNotificationEmail(recipient, subject, body);
            } catch (MailException | IllegalStateException exception) {
                LOGGER.warn("Alerte de retour {} non envoyée à un administrateur : {}", feedback.id(),
                        exception.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
