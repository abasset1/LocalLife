package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.collector.domain.ImportedActivityContact;
import com.locallife.backend.collector.infrastructure.ImportedActivityContactNoticeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class ImportedActivityContactNotifierTest {

    @Mock
    private ImportedActivityContactNoticeRepository repository;

    @Mock
    private EmailService emailService;

    private ImportedActivityContactNotifier notifier(boolean enabled) {
        return new ImportedActivityContactNotifier(repository, emailService, enabled, "https://locallife.test");
    }

    private static ImportedActivityContact contact(long id, String title, String email) {
        return new ImportedActivityContact(id, title, "Marseille", LocalDateTime.of(2026, 11, 14, 20, 0), email);
    }

    private void givenContacts(ImportedActivityContact... contacts) {
        when(repository.findPendingContacts(ImportedActivityContactNotifier.MAX_AGE_DAYS,
                ImportedActivityContactNotifier.COOLDOWN_DAYS, ImportedActivityContactNotifier.MAX_CONTACTS_PER_RUN))
                .thenReturn(List.of(contacts));
    }

    @Test
    void sendAll_ShouldSendOneNoticePerActivity_AndMarkItSent() {
        givenContacts(contact(7L, "Concert de jazz", "orga@example.com"));

        assertEquals(1, notifier(true).sendAll());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendImportedActivityNoticeEmail(eq("orga@example.com"),
                eq("Votre événement « Concert de jazz » est référencé sur LocalLife"), body.capture());
        assertTrue(body.getValue().contains("- Concert de jazz — Marseille, le 14/11/2026"));
        assertTrue(body.getValue().contains("Pour que cet événement continue d'être référencé sur LocalLife, "
                + "créez un compte professionnel"));
        assertTrue(body.getValue().contains("https://locallife.test/register"));
        assertTrue(body.getValue().contains("https://locallife.test/profile"));
        verify(repository).markSent("orga@example.com", List.of(7L));
    }

    @Test
    void sendAll_ShouldGroupActivitiesOfSameContact_InOneMessage() {
        givenContacts(contact(7L, "Concert", "orga@example.com"), contact(8L, "Atelier", "orga@example.com"),
                contact(9L, "Marché", "autre@example.com"));

        assertEquals(2, notifier(true).sendAll());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendImportedActivityNoticeEmail(eq("orga@example.com"),
                eq("Vos 2 événements sont référencés sur LocalLife"), body.capture());
        assertTrue(body.getValue().contains("- Concert"));
        assertTrue(body.getValue().contains("- Atelier"));
        assertFalse(body.getValue().contains("Marché"));
        assertTrue(body.getValue().contains("Pour que ces événements continuent d'être référencés"));
        verify(repository).markSent("orga@example.com", List.of(7L, 8L));
        verify(repository).markSent("autre@example.com", List.of(9L));
    }

    @Test
    void sendAll_ShouldNotMarkSent_WhenSendingFails_AndContinueWithNextContact() {
        givenContacts(contact(7L, "Concert", "orga@example.com"), contact(9L, "Marché", "autre@example.com"));
        doThrow(new MailSendException("SMTP indisponible")).when(emailService)
                .sendImportedActivityNoticeEmail(eq("orga@example.com"), anyString(), anyString());

        assertEquals(1, notifier(true).sendAll());

        verify(repository, never()).markSent(eq("orga@example.com"), any());
        verify(repository).markSent("autre@example.com", List.of(9L));
    }

    @Test
    void body_ShouldListAtMostTheConfiguredNumberOfActivities() {
        List<ImportedActivityContact> activities = LongStream.rangeClosed(1, 25)
                .mapToObj(id -> contact(id, "Événement " + id, "orga@example.com"))
                .toList();

        String body = notifier(true).body(activities);

        assertTrue(body.contains("- Événement 20 "));
        assertFalse(body.contains("- Événement 21 "));
        assertTrue(body.contains("… et 5 autre(s)."));
    }

    @Test
    void scheduledSend_ShouldDoNothing_WhenDisabled() {
        notifier(false).scheduledSend();

        verifyNoInteractions(repository, emailService);
    }

    @Test
    void scheduledSend_ShouldSend_WhenEnabled() {
        when(repository.findPendingContacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        notifier(true).scheduledSend();

        verify(repository).findPendingContacts(anyInt(), anyInt(), anyInt());
        verifyNoInteractions(emailService);
    }
}
