package com.locallife.backend.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.domain.AlertActivity;
import com.locallife.backend.alert.domain.AlertRecipient;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAlertNotifierTest {

    @Mock
    private ActivityAlertRepository activityAlertRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EmailService emailService;

    private ActivityAlertNotifier notifier;

    private final AlertRecipient alice = new AlertRecipient(
            new ActivityAlert(1L, 10L, "Lyon", 45.76, 4.83, 10, List.of("SPORT"), LocalDateTime.now()),
            "alice@example.com", "tok-alice");
    private final AlertRecipient bob = new AlertRecipient(
            new ActivityAlert(2L, 11L, "Paris", 48.85, 2.35, 5, List.of("SPORT"), LocalDateTime.now()),
            "bob@example.com", "tok-bob");

    @BeforeEach
    void setUp() {
        notifier = new ActivityAlertNotifier(activityAlertRepository, categoryRepository, emailService,
                "https://locallife.test");
    }

    private void givenCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(new Category(1L, "SPORT", true, LocalDateTime.now(),
                LocalDateTime.now(), Set.of(new CategoryTranslation(1L, "fr-FR", "Sport", null)))));
    }

    @Test
    void sendAll_ShouldSendDigest_AndMarkActivitiesSent() {
        givenCategories();
        when(activityAlertRepository.findAllRecipients()).thenReturn(List.of(alice));
        when(activityAlertRepository.findNewMatchingActivities(1L, ActivityAlertNotifier.MAX_ACTIVITIES_PER_RUN))
                .thenReturn(List.of(new AlertActivity(7L, "Match de foot", "Villeurbanne",
                        LocalDateTime.of(2026, 10, 18, 15, 0))));

        assertEquals(1, notifier.sendAll());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendActivityAlertEmail(
                eq("alice@example.com"), eq("LocalLife : 1 nouvelle activité près de Lyon"), body.capture());
        assertTrue(body.getValue().contains("« Sport » à moins de 10 km de Lyon"));
        assertTrue(body.getValue().contains("- Match de foot — Villeurbanne, le 18/10/2026"));
        assertTrue(body.getValue().contains("https://locallife.test/alerts/unsubscribe?token=tok-alice"));
        verify(activityAlertRepository).markSent(1L, List.of(7L));
    }

    @Test
    void sendAll_ShouldSendNothing_WhenNoNewActivity() {
        givenCategories();
        when(activityAlertRepository.findAllRecipients()).thenReturn(List.of(alice));
        when(activityAlertRepository.findNewMatchingActivities(1L, ActivityAlertNotifier.MAX_ACTIVITIES_PER_RUN))
                .thenReturn(List.of());

        assertEquals(0, notifier.sendAll());
        verify(emailService, never()).sendActivityAlertEmail(anyString(), anyString(), anyString());
        verify(activityAlertRepository, never()).markSent(any(), any());
    }

    @Test
    void sendAll_ShouldContinue_AndNotMarkSent_WhenOneEmailFails() {
        givenCategories();
        when(activityAlertRepository.findAllRecipients()).thenReturn(List.of(alice, bob));
        when(activityAlertRepository.findNewMatchingActivities(any(), eq(ActivityAlertNotifier.MAX_ACTIVITIES_PER_RUN)))
                .thenReturn(List.of(new AlertActivity(7L, "Match", null, null)));
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendActivityAlertEmail(eq("alice@example.com"), anyString(), anyString());

        assertEquals(1, notifier.sendAll());
        verify(activityAlertRepository, never()).markSent(eq(1L), any());
        verify(activityAlertRepository).markSent(2L, List.of(7L));
    }

    @Test
    void body_ShouldListAtMostTwentyActivities() {
        List<AlertActivity> activities = LongStream.rangeClosed(1, 25)
                .mapToObj(id -> new AlertActivity(id, "Activité " + id, null, null))
                .toList();

        String body = notifier.body(alice, activities, java.util.Map.of("SPORT", "Sport"));

        assertTrue(body.contains("- Activité 20\n"));
        assertTrue(!body.contains("- Activité 21\n"));
        assertTrue(body.contains("… et 5 autre(s)."));
    }

}
