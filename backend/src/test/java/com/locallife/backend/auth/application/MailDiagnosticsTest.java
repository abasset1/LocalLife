package com.locallife.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.application.MailDiagnostics.MailConfig;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class MailDiagnosticsTest {

    private static final MailConfig CONFIG =
            new MailConfig("smtp.example.com", 587, "user", true, true, true, false, "no-reply@example.com", null);

    @Test
    void explain_ShouldNameTheCauseAndTheFix() {
        assertThat(MailDiagnostics.explain(new AuthenticationFailedException("535"), CONFIG))
                .startsWith("Identifiants SMTP refusés par smtp.example.com:587");
        assertThat(MailDiagnostics.explain(new MessagingException("x", new ConnectException("refused")), CONFIG))
                .startsWith("Connexion refusée par smtp.example.com:587");
        assertThat(MailDiagnostics.explain(new MailSendException("x", new MessagingException("y",
                new SocketTimeoutException("Read timed out"))), CONFIG)).contains("ne répond pas").contains("465");
        assertThat(MailDiagnostics.explain(new MessagingException("x", new SSLHandshakeException("bad")), CONFIG))
                .startsWith("Échec du chiffrement");
        assertThat(MailDiagnostics.explain(new MessagingException("autre"), CONFIG))
                .isEqualTo("Échec de l'envoi via smtp.example.com:587 : autre");
    }

    @Test
    void warning_ShouldSpotInconsistentSettings() {
        assertThat(MailDiagnostics.warning("localhost", 1025, false, false, false, false)).contains("n'est pas configuré");
        assertThat(MailDiagnostics.warning("smtp.example.com", 465, true, true, false, true)).contains("SMTP_SSL=true");
        assertThat(MailDiagnostics.warning("smtp.example.com", 587, true, false, false, true)).contains("STARTTLS");
        assertThat(MailDiagnostics.warning("smtp.example.com", 587, true, true, false, false)).contains("SMTP_PASSWORD");
        assertThat(MailDiagnostics.warning("smtp.example.com", 587, true, true, false, true)).isNull();
    }
}
