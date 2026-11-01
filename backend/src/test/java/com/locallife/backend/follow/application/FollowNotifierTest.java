package com.locallife.backend.follow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.follow.domain.FollowRecipient;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowUpdate;
import com.locallife.backend.follow.infrastructure.FollowRepository;
import com.locallife.backend.push.application.ExpoPushService;
import com.locallife.backend.push.infrastructure.PushTokenRepository;
import com.locallife.backend.subscription.application.PlanLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FollowNotifierTest {

    private final FollowRepository followRepository = mock(FollowRepository.class);
    private final PlanLimits planLimits = mock(PlanLimits.class);
    private final EmailService emailService = mock(EmailService.class);
    private final ExpoPushService expoPushService = mock(ExpoPushService.class);
    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private FollowNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new FollowNotifier(followRepository, planLimits, emailService, expoPushService,
                pushTokenRepository, "https://locallife.example");
        when(planLimits.canFollow(7L)).thenReturn(true);
        when(followRepository.findRecipients()).thenReturn(List.of(new FollowRecipient(7L, "a@b.c")));
    }

    private static FollowUpdate activity(String title, List<String> kinds) {
        return new FollowUpdate(1L, FollowTargetType.ACTIVITY, 42L, title, kinds, new ArrayList<>(), 9);
    }

    private static FollowUpdate place(String title, List<String> newActivities) {
        return new FollowUpdate(2L, FollowTargetType.PLACE, 5L, title, List.of(), new ArrayList<>(newActivities), 0);
    }

    @Test
    void describe_ShouldListChangesInAReadableOrder() {
        assertThat(FollowNotifier.describe(activity("Concert", List.of("LOCATION", "DATES"))))
                .isEqualTo("dates modifiées, adresse modifiée");
        assertThat(FollowNotifier.describe(activity("Concert", List.of("CANCELLED")))).isEqualTo("annulée");
        assertThat(FollowNotifier.describe(place("Musée", List.of("Expo"))))
                .isEqualTo("nouvelle activité : Expo");
        assertThat(FollowNotifier.describe(place("Musée", List.of("Expo", "Atelier"))))
                .isEqualTo("2 nouvelles activités");
    }

    @Test
    void titleAndPushData_ShouldPointToTheActivityWhenThereIsOnlyOne() {
        List<FollowUpdate> single = List.of(activity("Concert", List.of("DATES")));
        assertThat(FollowNotifier.title(single)).isEqualTo("Du nouveau : Concert");
        assertThat(FollowNotifier.pushBody(single)).isEqualTo("Dates modifiées");
        assertThat(FollowNotifier.pushData(single)).isEqualTo(Map.of("type", "follow-update", "activityId", 42L));

        List<FollowUpdate> several = List.of(activity("Concert", List.of("DATES")), place("Musée", List.of("Expo")));
        assertThat(FollowNotifier.title(several)).isEqualTo("Du nouveau sur 2 activités et lieux suivis");
        assertThat(FollowNotifier.pushData(several)).isEqualTo(Map.of("type", "follow-update"));
    }

    @Test
    void send_ShouldUsePushWhenADeviceIsRegistered_AndNotEmail() {
        when(followRepository.findPendingUpdates(7L, FollowNotifier.MAX_NEW_ACTIVITIES_PER_PLACE))
                .thenReturn(List.of(activity("Concert", List.of("CANCELLED"))));
        when(pushTokenRepository.findTokensByUserId(7L)).thenReturn(List.of("ExponentPushToken[x]"));
        when(expoPushService.send(any(), anyString(), anyString(), any())).thenReturn(List.of());

        assertThat(notifier.sendAll()).isEqualTo(1);

        verify(expoPushService).send(eq(List.of("ExponentPushToken[x]")), eq("Du nouveau : Concert"), eq("Annulée"),
                any());
        verify(emailService, never()).sendActivityAlertEmail(anyString(), anyString(), anyString());
        verify(followRepository).markNotified(eq(1L), eq(9L), any());
    }

    @Test
    void send_ShouldEmailWithoutDevice() {
        when(followRepository.findPendingUpdates(7L, FollowNotifier.MAX_NEW_ACTIVITIES_PER_PLACE))
                .thenReturn(List.of(place("Musée des Beaux-Arts", List.of("Expo", "Atelier"))));
        when(pushTokenRepository.findTokensByUserId(7L)).thenReturn(List.of());

        assertThat(notifier.sendAll()).isEqualTo(1);

        verify(emailService).sendActivityAlertEmail(eq("a@b.c"), eq("LocalLife : Du nouveau : Musée des Beaux-Arts"),
                org.mockito.ArgumentMatchers.contains("Lieu « Musée des Beaux-Arts » : 2 nouvelles activités"));
    }

    @Test
    void send_ShouldSkipAndAdvanceAnAccountThatNoLongerHasAccess() {
        when(planLimits.canFollow(7L)).thenReturn(false);

        assertThat(notifier.sendAll()).isZero();

        verify(followRepository).markAllNotified(eq(7L), any());
        verify(followRepository, never()).findPendingUpdates(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }
}
