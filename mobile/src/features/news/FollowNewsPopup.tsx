import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import {
  loadFollowNewsPopupSeen,
  saveFollowNewsDismissed,
  saveFollowNewsPopupSeen,
  shouldShowFollowNewsPopup,
  wasOnboardingShown,
} from './followNews';

/**
 * Popup montrée une seule fois à un membre gratuit, par compte et par appareil, quand la carte est à l'écran :
 * annonce la cloche « Suivre », les alertes par mots-clés et Premium gratuit pour le moment. « Découvrir » ouvre l'écran « Nouveautés » (et ferme
 * aussi le bandeau de la carte) ; « Plus tard » ou le bouton retour la ferment en laissant le bandeau comme rappel.
 * Marquée vue dès son affichage. Pas de popup pendant une session où l'accueil de première connexion s'est ouvert.
 */
export function FollowNewsPopup() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const canFollow = useCurrentAccount(userId !== null)?.limits?.canFollow;
  const [seen, setSeen] = useState<{ userId: number; value: boolean } | null>(null);
  const [focused, setFocused] = useState(false);
  /** Popup fermée pour ce compte pendant cette session. */
  const [closedFor, setClosedFor] = useState<number | null>(null);

  useFocusEffect(
    useCallback(() => {
      setFocused(true);
      return () => setFocused(false);
    }, []),
  );

  useEffect(() => {
    if (userId === null) return;
    let current = true;
    void loadFollowNewsPopupSeen(userId).then((value) => {
      if (current) setSeen({ userId, value });
    });
    return () => {
      current = false;
    };
  }, [userId]);

  const seenForUser = seen && seen.userId === userId ? seen.value : null;
  const eligible = focused && shouldShowFollowNewsPopup(userId, canFollow, seenForUser, wasOnboardingShown());

  const open = eligible && closedFor !== userId;

  useEffect(() => {
    // Marquée vue dès l'affichage : elle ne reviendra pas, même si l'application est fermée sans répondre.
    if (open && userId !== null) void saveFollowNewsPopupSeen(userId);
  }, [open, userId]);

  function close() {
    setClosedFor(userId);
  }

  function discover() {
    close();
    if (userId !== null) void saveFollowNewsDismissed(userId);
    router.push('/nouveautes');
  }

  return (
    <Modal visible={open} transparent animationType="fade" onRequestClose={close}>
      <Pressable accessibilityLabel="Fermer" style={styles.backdrop} onPress={close}>
        <Pressable
          accessibilityViewIsModal
          onPress={() => undefined}
          style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
          <View style={[styles.badge, { backgroundColor: theme.accent }]}>
            <ThemedText type="smallBold" style={styles.badgeText}>
              NOUVEAU
            </ThemedText>
          </View>
          <ThemedText style={styles.bell} importantForAccessibility="no">
            🔔
          </ThemedText>
          <ThemedText type="heading" accessibilityRole="header" style={styles.center}>
            Deux nouveautés Premium
          </ThemedText>
          <View style={[styles.feature, { borderColor: theme.border }]}>
            <ThemedText type="smallBold">🔔 Suivez vos activités et vos lieux préférés</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              LocalLife vous prévient quand leurs dates changent, qu’une activité est annulée ou qu’un lieu en
              programme une nouvelle.
            </ThemedText>
          </View>
          <View style={[styles.feature, { borderColor: theme.border }]}>
            <ThemedText type="smallBold">🔎 Des alertes sur mesure, avec vos mots-clés</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              « jazz », « marché de Noël »… : ne recevez que ce qui vous intéresse vraiment.
            </ThemedText>
          </View>
          <View style={styles.free}>
            <ThemedText type="smallBold" style={styles.freeText}>
              🎁 Premium est gratuit pour le moment
            </ThemedText>
          </View>
          <View style={styles.actions}>
            <Button label="Découvrir" onPress={discover} />
            <Button label="Plus tard" variant="secondary" onPress={close} />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    backgroundColor: 'rgba(23, 32, 29, 0.55)',
  },
  card: {
    width: '100%',
    maxWidth: 420,
    alignItems: 'center',
    gap: Spacing.three,
    padding: Spacing.four,
    borderRadius: Radius.xl,
  },
  badge: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.half,
    borderRadius: 999,
  },
  badgeText: {
    color: '#FFFFFF',
    letterSpacing: 1,
  },
  bell: {
    fontSize: 48,
    lineHeight: 58,
  },
  center: {
    textAlign: 'center',
  },
  feature: {
    alignSelf: 'stretch',
    gap: Spacing.half,
    padding: Spacing.three,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  free: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
    backgroundColor: '#F2C14E',
  },
  freeText: {
    color: '#17201D',
    textAlign: 'center',
  },
  actions: {
    alignSelf: 'stretch',
    gap: Spacing.two,
  },
});
