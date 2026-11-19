package com.locallife.backend.auth.application;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Implémentation SMTP de {@link EmailService} (LL-EF-007), via
 * {@link JavaMailSender} (spring-boot-starter-mail). Configuration SMTP
 * dans {@code application.properties} (préfixe {@code spring.mail.*}),
 * fournie par variables d'environnement en production.
 *
 * <p>RGPD (22/10/2026) : l'adresse du destinataire n'apparaît dans les journaux que masquée
 * ({@link EmailMask}).
 *
 * <p>⚠️ Ne jamais logger {@code resetLink} ni {@code verificationLink} (contiennent le token en clair) —
 * un seul log au niveau INFO confirmant l'envoi, sans son contenu, pour
 * respecter l'exigence de non-fuite du token dans les logs.
 *
 * <p>Expéditeur {@code MAIL_FROM} (forme « Neiri &lt;no-reply@domaine&gt; » acceptée) ; adresse de réponse
 * facultative {@code MAIL_REPLY_TO} (une boîte lue, quand l'expéditeur est une adresse « no-reply »).
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String replyTo;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail-from:no-reply@neiri.local}") String fromAddress,
            @Value("${app.mail-reply-to:}") String replyTo) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo == null ? "" : replyTo.trim();
    }

    /** Envoi texte en UTF-8 ; {@code oneClickUnsubscribeUrl} non nul : en-têtes de désinscription (RFC 8058). */
    void send(String to, String subject, String text, String oneClickUnsubscribeUrl) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            if (!replyTo.isEmpty()) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            if (oneClickUnsubscribeUrl != null && !oneClickUnsubscribeUrl.isBlank()) {
                message.setHeader("List-Unsubscribe", "<" + oneClickUnsubscribeUrl + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
        } catch (MessagingException exception) {
            throw new MailPreparationException("Préparation de l'e-mail impossible", exception);
        }
        mailSender.send(message);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        send(to, "Réinitialisation de votre mot de passe Neiri",
                "Bonjour,\n\n"
                        + "Une demande de réinitialisation de mot de passe a été effectuée pour ce compte.\n\n"
                        + "Pour définir un nouveau mot de passe, cliquez sur le lien suivant "
                        + "(valable 30 minutes, à usage unique) :\n"
                        + resetLink + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email : "
                        + "votre mot de passe actuel reste valide.\n\n"
                        + "L'équipe Neiri", null);
        log.info("Email de réinitialisation de mot de passe envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendEmailVerificationEmail(String to, String verificationLink) {
        send(to, "Confirmez votre adresse email Neiri",
                "Bonjour,\n\n"
                        + "Bienvenue sur Neiri ! Pour activer votre compte, confirmez votre adresse email "
                        + "en cliquant sur le lien suivant (valable 24 heures) :\n"
                        + verificationLink + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette inscription, vous pouvez ignorer cet email : "
                        + "aucun compte ne sera activé.\n\n"
                        + "L'équipe Neiri", null);
        log.info("Email de confirmation d'adresse envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendActivityAlertEmail(String to, String subject, String text) {
        send(to, subject, text, null);
        log.info("Email d'alerte d'activités envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendActivityAlertEmail(String to, String subject, String text, String oneClickUnsubscribeUrl) {
        send(to, subject, text, oneClickUnsubscribeUrl);
        log.info("Email d'alerte d'activités envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendEmailChangeConfirmationEmail(String to, String confirmationLink) {
        send(to, "Confirmez votre nouvelle adresse email Neiri",
                "Bonjour,\n\n"
                        + "Vous avez demandé à utiliser cette adresse pour votre compte Neiri. Pour confirmer, "
                        + "cliquez sur le lien suivant (valable 24 heures) :\n"
                        + confirmationLink + "\n\n"
                        + "Tant que vous n'avez pas confirmé, votre ancienne adresse reste celle du compte. "
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n"
                        + "L'équipe Neiri", null);
        log.info("Email de confirmation de changement d'adresse envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendEmailChangeNoticeEmail(String to, String newEmailMasked) {
        send(to, "Changement d'adresse email demandé sur votre compte Neiri",
                "Bonjour,\n\n"
                        + "Un changement d'adresse email vers " + newEmailMasked + " a été demandé pour votre "
                        + "compte Neiri. Il ne prendra effet qu'après confirmation depuis la nouvelle adresse.\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, changez votre mot de passe depuis "
                        + "votre profil (ou via « mot de passe oublié ») et contactez l'équipe Neiri.\n\n"
                        + "L'équipe Neiri", null);
        log.info("Avis de changement d'adresse envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendProDecisionEmail(String to, String subject, String text) {
        send(to, subject, text, null);
        log.info("Décision professionnelle envoyée à {} : {}", EmailMask.mask(to), subject);
    }

    @Override
    public void sendImportedActivityNoticeEmail(String to, String subject, String text) {
        send(to, subject, text, null);
        log.info("Avis de référencement d'activité importée envoyé à {}", EmailMask.mask(to));
    }

    @Override
    public void sendAdminNotificationEmail(String to, String subject, String text) {
        send(to, subject, text, null);
        log.info("Alerte administrateur envoyée à {} : {}", EmailMask.mask(to), subject);
    }
}
