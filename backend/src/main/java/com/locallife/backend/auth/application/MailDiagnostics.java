package com.locallife.backend.auth.application;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Properties;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Diagnostic de l'envoi d'e-mails pour l'administration : configuration SMTP effective (sans le mot de passe), test
 * de connexion et envoi d'un message de test, avec une explication lisible de l'échec. Les envois réels
 * (confirmation d'adresse, mot de passe oublié, alertes) ne journalisaient qu'une trace technique : une mauvaise
 * configuration restait invisible depuis l'interface.
 */
@Service
public class MailDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailDiagnostics.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailDiagnostics(JavaMailSender mailSender, @Value("${app.mail-from:no-reply@locallife.local}") String from) {
        this.mailSender = mailSender;
        this.fromAddress = from;
    }

    /** Configuration effective, sans le mot de passe. */
    public record MailConfig(
            String host, int port, String username, boolean passwordSet, boolean auth, boolean startTls, boolean ssl,
            String from, String warning) {
    }

    /** Résultat d'un test : réussi, ou l'étape en échec et son explication. */
    public record MailTestResult(boolean ok, String step, String message) {
    }

    public MailConfig config() {
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            return new MailConfig(null, 0, null, false, false, false, false, fromAddress, null);
        }
        Properties properties = sender.getJavaMailProperties();
        boolean auth = Boolean.parseBoolean(properties.getProperty("mail.smtp.auth", "false"));
        boolean startTls = Boolean.parseBoolean(properties.getProperty("mail.smtp.starttls.enable", "false"));
        boolean ssl = Boolean.parseBoolean(properties.getProperty("mail.smtp.ssl.enable", "false"));
        boolean passwordSet = sender.getPassword() != null && !sender.getPassword().isBlank();
        return new MailConfig(sender.getHost(), sender.getPort(), sender.getUsername(), passwordSet, auth, startTls,
                ssl, fromAddress, warning(sender.getHost(), sender.getPort(), auth, startTls, ssl, passwordSet));
    }

    /** Teste la connexion au serveur SMTP, puis envoie un message de test à {@code to}. */
    public MailTestResult sendTest(String to) {
        if (mailSender instanceof JavaMailSenderImpl sender) {
            try {
                sender.testConnection();
            } catch (MessagingException exception) {
                LOGGER.warn("Test SMTP : connexion impossible : {}", exception.toString());
                return new MailTestResult(false, "connexion", explain(exception, config()));
            }
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("LocalLife : e-mail de test");
            message.setText("Bonjour,\n\nCet e-mail de test confirme que LocalLife peut envoyer des messages.\n\n"
                    + "L'équipe LocalLife");
            mailSender.send(message);
            LOGGER.info("E-mail de test envoyé à {}", to);
            return new MailTestResult(true, "envoi", "E-mail de test envoyé à " + to
                    + ". S'il n'arrive pas, regarde les indésirables, puis la configuration de l'expéditeur "
                    + "(SPF/DKIM).");
        } catch (MailException exception) {
            LOGGER.warn("Test SMTP : envoi impossible : {}", exception.toString());
            return new MailTestResult(false, "envoi", explain(exception, config()));
        }
    }

    /** Explication d'un échec SMTP, à partir de la cause la plus profonde reconnue. */
    static String explain(Throwable exception, MailConfig config) {
        String server = config.host() + ":" + config.port();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AuthenticationFailedException || cause instanceof MailAuthenticationException) {
                return "Identifiants SMTP refusés par " + server + ". Vérifie SMTP_USERNAME et SMTP_PASSWORD (Gmail, "
                        + "Outlook : un mot de passe d'application est exigé).";
            }
            if (cause instanceof UnknownHostException) {
                return "Serveur SMTP inconnu : « " + config.host() + " ». Vérifie SMTP_HOST.";
            }
            if (cause instanceof ConnectException) {
                return "Connexion refusée par " + server + ". Vérifie SMTP_HOST et SMTP_PORT, et que le serveur "
                        + "autorise les connexions sortantes vers ce port.";
            }
            if (cause instanceof SocketTimeoutException) {
                return "Le serveur " + server + " ne répond pas (délai dépassé). Port bloqué, ou chiffrement mal "
                        + "choisi : port 465 → SMTP_SSL=true et SMTP_STARTTLS=false ; port 587 → SMTP_STARTTLS=true.";
            }
            if (cause instanceof SSLException) {
                return "Échec du chiffrement avec " + server + ". Port 465 → SMTP_SSL=true et SMTP_STARTTLS=false ; "
                        + "port 587 → SMTP_STARTTLS=true et SMTP_SSL=false.";
            }
            if (cause instanceof SendFailedException) {
                return "Adresse refusée par le serveur : l'expéditeur MAIL_FROM (« " + config.from() + " ») doit être "
                        + "une adresse autorisée par ton prestataire, et le destinataire doit être valide.";
            }
        }
        String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return "Échec de l'envoi via " + server + " : " + detail;
    }

    /** Erreurs de configuration visibles sans rien envoyer. */
    static String warning(String host, int port, boolean auth, boolean startTls, boolean ssl, boolean passwordSet) {
        if (host == null || host.isBlank() || "localhost".equals(host) && port == 1025) {
            return "SMTP_HOST n'est pas configuré : l'application utilise le serveur de développement localhost:1025.";
        }
        if (port == 465 && !ssl) {
            return "Port 465 sans SMTP_SSL=true : ce port attend une connexion chiffrée dès le départ.";
        }
        if (port == 587 && !startTls && !ssl) {
            return "Port 587 sans SMTP_STARTTLS=true : la plupart des prestataires exigent STARTTLS sur ce port.";
        }
        if (auth && !passwordSet) {
            return "Authentification activée (SMTP_AUTH=true) mais SMTP_PASSWORD est vide.";
        }
        if (!auth && passwordSet) {
            return "SMTP_PASSWORD est fourni mais SMTP_AUTH=false : les identifiants ne sont pas utilisés.";
        }
        return null;
    }
}
