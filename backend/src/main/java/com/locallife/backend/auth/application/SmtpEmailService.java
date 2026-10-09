package com.locallife.backend.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Implémentation SMTP de {@link EmailService} (LL-EF-007), via
 * {@link JavaMailSender} (spring-boot-starter-mail). Configuration SMTP
 * dans {@code application.properties} (préfixe {@code spring.mail.*}),
 * fournie par variables d'environnement en production.
 *
 * <p>⚠️ Ne jamais logger {@code resetLink} ni {@code verificationLink} (contiennent le token en clair) —
 * un seul log au niveau INFO confirmant l'envoi, sans son contenu, pour
 * respecter l'exigence de non-fuite du token dans les logs.
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail-from:no-reply@locallife.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Réinitialisation de votre mot de passe LocalLife");
        message.setText(
                "Bonjour,\n\n"
                        + "Une demande de réinitialisation de mot de passe a été effectuée pour ce compte.\n\n"
                        + "Pour définir un nouveau mot de passe, cliquez sur le lien suivant "
                        + "(valable 30 minutes, à usage unique) :\n"
                        + resetLink + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email : "
                        + "votre mot de passe actuel reste valide.\n\n"
                        + "L'équipe LocalLife");

        mailSender.send(message);
        log.info("Email de réinitialisation de mot de passe envoyé à {}", to);
    }

    @Override
    public void sendEmailVerificationEmail(String to, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Confirmez votre adresse email LocalLife");
        message.setText(
                "Bonjour,\n\n"
                        + "Bienvenue sur LocalLife ! Pour activer votre compte, confirmez votre adresse email "
                        + "en cliquant sur le lien suivant (valable 24 heures) :\n"
                        + verificationLink + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette inscription, vous pouvez ignorer cet email : "
                        + "aucun compte ne sera activé.\n\n"
                        + "L'équipe LocalLife");

        mailSender.send(message);
        log.info("Email de confirmation d'adresse envoyé à {}", to);
    }

    @Override
    public void sendActivityAlertEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
        log.info("Email d'alerte d'activités envoyé à {}", to);
    }

}
