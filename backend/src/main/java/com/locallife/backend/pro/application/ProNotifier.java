package com.locallife.backend.pro.application;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Prévient un professionnel par e-mail d'une décision de l'équipe (décision du 22/10/2026) : offre Pro, demande de
 * gestion, modification de lieu, food truck. Envoyé après la validation de la transaction (jamais pour une décision
 * annulée) ; un échec d'envoi est journalisé sans remettre la décision en cause.
 */
@Service
public class ProNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProNotifier.class);
    private static final String SIGNATURE = "\n\nL'équipe LocalLife";

    private final EmailService emailService;
    private final UserRepository userRepository;

    public ProNotifier(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    /** {@code reason} facultatif, ajouté au texte quand il est fourni. */
    public void notify(Long userId, String subject, String text, String reason) {
        if (userId == null) {
            return;
        }
        String email = userRepository.findById(userId).map(User::email).orElse(null);
        if (email == null) {
            return;
        }
        String body = "Bonjour,\n\n" + text
                + (reason == null || reason.isBlank() ? "" : "\n\nMotif : " + reason.trim()) + SIGNATURE;
        Runnable send = () -> {
            try {
                emailService.sendProDecisionEmail(email, subject, body);
            } catch (MailException | IllegalStateException exception) {
                LOGGER.warn("E-mail de décision non envoyé à l'utilisateur {} : {}", userId, exception.getMessage());
            }
        };
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
