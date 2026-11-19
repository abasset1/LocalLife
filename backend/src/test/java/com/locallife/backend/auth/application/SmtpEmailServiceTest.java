package com.locallife.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private MimeMessage sent(SmtpEmailService service, Runnable action) {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        action.run();
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void alert_ShouldCarryOneClickUnsubscribeHeaders_AndReplyTo() throws Exception {
        SmtpEmailService service = new SmtpEmailService(mailSender, "Neiri <no-reply@neiri.test>",
                "contact@neiri.test");

        MimeMessage message = sent(service, () -> service.sendActivityAlertEmail("alice@example.com",
                "Neiri : 2 nouveautés", "Bonjour é", "https://neiri.test/api/v1/alerts/unsubscribe/one-click?token=t"));

        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Neiri");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("contact@neiri.test");
        assertThat(message.getHeader("List-Unsubscribe")[0])
                .isEqualTo("<https://neiri.test/api/v1/alerts/unsubscribe/one-click?token=t>");
        assertThat(message.getHeader("List-Unsubscribe-Post")[0]).isEqualTo("List-Unsubscribe=One-Click");
        assertThat(message.getSubject()).isEqualTo("Neiri : 2 nouveautés");
        assertThat(message.getContent()).isEqualTo("Bonjour é");
    }

    @Test
    void transactional_ShouldHaveNoUnsubscribeHeader_AndNoReplyToWhenUnset() throws Exception {
        SmtpEmailService service = new SmtpEmailService(mailSender, "no-reply@neiri.test", "");

        MimeMessage message = sent(service,
                () -> service.sendPasswordResetEmail("bob@example.com", "https://neiri.test/reset?token=x"));

        assertThat(message.getHeader("List-Unsubscribe")).isNull();
        assertThat(message.getHeader("Reply-To")).isNull();
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@neiri.test");
    }
}
