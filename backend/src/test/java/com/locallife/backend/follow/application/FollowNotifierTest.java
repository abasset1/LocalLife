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
import com.locallife.backend.follow.domain.FieldChange;
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
                .isEqualTo("Dates modifiées · Adresse modifiée");
        assertThat(FollowNotifier.describe(activity("Concert", List.of("CANCELLED")))).isEqualTo("Annulée");
        assertThat(FollowNotifier.describe(place("Musée", List.of("Expo"))))
                .isEqualTo("Nouvelle activité : Expo");
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
        assertThat(FollowNotifier.title(several)).isEqualTo("Du nouveau sur 2 de tes suivis");
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

        verify(emailService).sendActivityAlertEmail(eq("a@b.c"), eq("Neiri : Du nouveau : Musée des Beaux-Arts"),
                org.mockito.ArgumentMatchers.contains("📍 Lieu « Musée des Beaux-Arts »\n  • 2 nouvelles activités\n"
                        + "      · Expo\n      · Atelier\n"));
    }

    @Test
    void send_ShouldSkipAndAdvanceAnAccountThatNoLongerHasAccess() {
        when(planLimits.canFollow(7L)).thenReturn(false);

        assertThat(notifier.sendAll()).isZero();

        verify(followRepository).markAllNotified(eq(7L), any());
        verify(followRepository, never()).findPendingUpdates(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static FollowUpdate foodTruck(List<String> kinds, List<String> presentToday) {
        return new FollowUpdate(3L, FollowTargetType.FOOD_TRUCK, 8L, "Chez Marco", kinds, List.of(), 0,
                new ArrayList<>(presentToday));
    }

    @Test
    void aFoodTruckPresentToday_ShouldSayWhereToFindIt_AndOpenItsPage() {
        List<FollowUpdate> single = List.of(foodTruck(List.of(), List.of("Place du marché, 11:30–14:00")));
        assertThat(FollowNotifier.title(single)).isEqualTo("Chez Marco est là aujourd'hui");
        assertThat(FollowNotifier.pushBody(single)).isEqualTo("Aujourd'hui : Place du marché, 11:30–14:00");
        assertThat(FollowNotifier.pushData(single)).isEqualTo(Map.of("type", "follow-update", "foodTruckId", 8L));

        FollowUpdate changed = foodTruck(List.of("PLANNING"), List.of("Gare", "Port"));
        assertThat(FollowNotifier.title(List.of(changed))).isEqualTo("Du nouveau : Chez Marco");
        assertThat(FollowNotifier.describe(changed)).isEqualTo("Planning mis à jour · Aujourd'hui : Gare · Port");
        assertThat(FollowNotifier.pushData(List.of(foodTruck(List.of("CLOSED"), List.of()))))
                .isEqualTo(Map.of("type", "follow-update"));
    }

    @Test
    void send_ShouldRememberThatTodaysPresenceWasAnnounced() {
        when(followRepository.findPendingUpdates(7L, FollowNotifier.MAX_NEW_ACTIVITIES_PER_PLACE))
                .thenReturn(List.of(foodTruck(List.of(), List.of("Gare"))));
        when(pushTokenRepository.findTokensByUserId(7L)).thenReturn(List.of());

        assertThat(notifier.sendAll()).isEqualTo(1);

        verify(emailService).sendActivityAlertEmail(eq("a@b.c"), eq("Neiri : Chez Marco est là aujourd'hui"),
                org.mockito.ArgumentMatchers.contains("🚚 Food truck « Chez Marco »\n  • Aujourd'hui : Gare\n"));
        verify(followRepository).markPresenceNotified(3L);
    }

    private static FollowUpdate activityWithChanges(List<String> kinds, List<FieldChange> changes) {
        return new FollowUpdate(1L, FollowTargetType.ACTIVITY, 42L, "Concert au port", kinds, List.of(), 9, List.of(),
                changes);
    }

    @Test
    void describe_ShouldSayWhatChanged_WhenTheDetailIsKnown() {
        FollowUpdate update = activityWithChanges(List.of("DATES", "LOCATION", "DETAILS"), List.of(
                new FieldChange("start_date", "2026-11-22T20:30:00", "2026-11-23T21:00:00"),
                new FieldChange("address", "1 rue A, 84000 Avignon", "3 quai B, 84000 Avignon"),
                new FieldChange("title", "Concert", "Concert au port"),
                new FieldChange("description", null, null),
                new FieldChange("long_description", null, null),
                new FieldChange("conditions", "10 €", "Plein tarif : 12 €")));

        assertThat(FollowNotifier.lines(update)).containsExactly(
                "Date : dimanche 22 novembre à 20 h 30 → lundi 23 novembre à 21 h",
                "Adresse : 1 rue A, 84000 Avignon → 3 quai B, 84000 Avignon",
                "Titre : « Concert » → « Concert au port »",
                "Description mise à jour",
                "Tarifs et conditions : Plein tarif : 12 €");
        assertThat(FollowNotifier.pushBody(List.of(update))).startsWith(
                "Date : dimanche 22 novembre à 20 h 30 → lundi 23 novembre à 21 h · Adresse : ");
    }

    @Test
    void formatChange_ShouldHandleAddedRemovedAndSpecialValues() {
        assertThat(FollowNotifier.formatChange(new FieldChange("end_date", null, "2026-11-30T00:00:00")))
                .isEqualTo("Fin : lundi 30 novembre");
        assertThat(FollowNotifier.formatChange(new FieldChange("phone", "04 90 00 00 00", null)))
                .isEqualTo("Téléphone : retiré (avant : 04 90 00 00 00)");
        assertThat(FollowNotifier.formatChange(new FieldChange("cuisine_type", "BURGER", "PIZZA")))
                .isEqualTo("Type de cuisine : Burgers → Pizzas");
        assertThat(FollowNotifier.formatChange(new FieldChange("age_min", "6", "8")))
                .isEqualTo("Âge minimum : 6 ans → 8 ans");
        assertThat(FollowNotifier.formatChange(new FieldChange("website", "https://a.fr", "https://b.fr")))
                .isEqualTo("Site web : https://b.fr");
        assertThat(FollowNotifier.formatChange(new FieldChange("position", null, null)))
                .isEqualTo("Emplacement précisé sur la carte");
    }

    @Test
    void describe_ShouldKeepTheKindLabel_ForAChangeWithoutDetail() {
        // Changement enregistré avant V94, ou retour en publication : pas de détail.
        FollowUpdate update = activityWithChanges(List.of("DETAILS", "CANCELLED"), List.of());

        assertThat(FollowNotifier.describe(update)).isEqualTo("Annulée · Informations mises à jour");
    }

    @Test
    void emailBody_ShouldShowEachChange_AndLinkToTheActivity() {
        String body = notifier.emailBody(List.of(activityWithChanges(List.of("DATES"),
                List.of(new FieldChange("start_date", "2026-11-22T20:30:00", "2026-11-23T20:30:00")))));

        assertThat(body).contains("Voici ce qui a changé dans ce que tu suis :\n\n"
                + "📅 Activité « Concert au port »\n"
                + "  • Date : dimanche 22 novembre à 20 h 30 → lundi 23 novembre à 20 h 30\n"
                + "  → Voir l'activité : https://locallife.example/?activity=42\n");
        assertThat(notifier.emailBody(List.of(activityWithChanges(List.of("DELETED"), List.of()))))
                .contains("  • Retirée de Neiri\n").doesNotContain("Voir l'activité");
    }
}
