import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { ApiError, subscribePlan } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import { wasOnboardingShown } from './followNews';
import {
  closePremiumOfferForSession,
  loadNewsSeenBeforeSession,
  loadPremiumOfferOptOut,
  savePremiumOfferOptOut,
  shouldShowPremiumOffer,
  wasPremiumOfferClosed,
} from './premiumOffer';

const BENEFITS = [
  { icon: '🔔', text: 'Suivez vos activités et vos lieux préférés : prévenu au moindre changement' },
  { icon: '🔎', text: 'Des alertes par mots-clés : seulement ce qui vous intéresse' },
  { icon: '❤️', text: 'Favoris illimités et jusqu’à 10 alertes' },
  { icon: '✍️', text: 'Proposez vos propres activités sur la carte' },
];

/**
 * Popup qui propose Premium aux comptes gratuits, quand la carte est à l'écran, comme sur le site web : à chaque
 * lancement de l'application pendant 7 jours après chaque connexion. « Plus tard », le bouton retour ou un appui à
 * côté la ferment jusqu'au lancement suivant ; « Ne plus me proposer » l'arrête pour ce compte sur cet appareil.
 * Elle laisse passer d'abord la popup de nouveauté « Suivre » et l'accueil de première connexion (jamais deux
 * popups à la suite). « Passer à Premium » l'active tout de suite (gratuit pour le moment).
 */
export function PremiumOfferPopup() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : null;
  const userId = user?.userId ?? null;
  const canFollow = useCurrentAccount(userId !== null)?.limits?.canFollow;
  const [stored, setStored] = useState<{ userId: number; optedOut: boolean; newsSeenBefore: boolean } | null>(null);
  /** Heure à laquelle la carte a pris le focus ; `null` quand elle ne l'a pas. */
  const [focusedAt, setFocusedAt] = useState<number | null>(null);
  const [closedFor, setClosedFor] = useState<number | null>(null);
  const [subscribedFor, setSubscribedFor] = useState<number | null>(null);
  const [subscribing, setSubscribing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      setFocusedAt(Date.now());
      return () => setFocusedAt(null);
    }, []),
  );

  useEffect(() => {
    if (userId === null) return;
    let current = true;
    void Promise.all([loadPremiumOfferOptOut(userId), loadNewsSeenBeforeSession(userId)]).then(
      ([optedOut, newsSeenBefore]) => {
        if (current) setStored({ userId, optedOut, newsSeenBefore });
      },
    );
    return () => {
      current = false;
    };
  }, [userId]);

  const storedForUser = stored && stored.userId === userId ? stored : null;
  const subscribed = userId !== null && subscribedFor === userId;
  const eligible =
    focusedAt !== null &&
    shouldShowPremiumOffer({
      userId,
      canFollow,
      loggedInAt: user?.loggedInAt,
      nowMs: focusedAt,
      optedOut: storedForUser?.optedOut ?? null,
      closedThisSession: userId !== null && wasPremiumOfferClosed(userId),
      newsSeenBefore: storedForUser?.newsSeenBefore ?? null,
      onboardingShown: wasOnboardingShown(),
    });
  const open = closedFor !== userId && (eligible || subscribed);

  function close() {
    if (userId !== null) closePremiumOfferForSession(userId);
    setClosedFor(userId);
  }

  function optOut() {
    if (userId !== null) void savePremiumOfferOptOut(userId);
    close();
  }

  async function subscribe() {
    setSubscribing(true);
    setError(null);
    try {
      await subscribePlan('PREMIUM');
      setSubscribedFor(userId);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
    } finally {
      setSubscribing(false);
    }
  }

  function seeNews() {
    close();
    router.push('/nouveautes');
  }

  return (
    <Modal visible={open} transparent animationType="fade" onRequestClose={close}>
      <Pressable accessibilityLabel="Fermer" style={styles.backdrop} onPress={close}>
        <Pressable
          accessibilityViewIsModal
          onPress={() => undefined}
          style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
          {subscribed ? (
            <>
              <ThemedText style={styles.icon} importantForAccessibility="no">
                🎉
              </ThemedText>
              <ThemedText type="heading" accessibilityRole="header" style={styles.center}>
                Bienvenue dans Premium !
              </ThemedText>
              <ThemedText themeColor="textSecondary" style={styles.center}>
                Tout est débloqué. Ouvrez une activité ou un lieu et touchez 🔔 « Suivre » pour commencer.
              </ThemedText>
              <View style={styles.actions}>
                <Button label="C’est parti" onPress={close} />
                <Button label="Voir les nouveautés" variant="secondary" onPress={seeNews} />
              </View>
            </>
          ) : (
            <>
              <ThemedText style={styles.icon} importantForAccessibility="no">
                ✨
              </ThemedText>
              <ThemedText type="heading" accessibilityRole="header" style={styles.center}>
                Passez à Premium
              </ThemedText>
              <ThemedText themeColor="textSecondary" style={styles.center}>
                Ne ratez plus rien de ce qui se passe près de chez vous.
              </ThemedText>
              <View style={styles.benefits}>
                {BENEFITS.map((benefit) => (
                  <View key={benefit.text} style={styles.benefit}>
                    <ThemedText importantForAccessibility="no">{benefit.icon}</ThemedText>
                    <ThemedText type="small" style={styles.benefitText}>
                      {benefit.text}
                    </ThemedText>
                  </View>
                ))}
              </View>
              <View style={styles.free}>
                <ThemedText type="smallBold" style={styles.freeText}>
                  🎁 Gratuit pour le moment
                </ThemedText>
              </View>
              <View style={styles.actions}>
                <Button label="Passer à Premium" loading={subscribing} onPress={() => void subscribe()} />
                <Button label="Plus tard" variant="secondary" onPress={close} />
              </View>
              {error && <Notice tone="danger">Erreur : {error}</Notice>}
              <Button label="Ne plus me proposer" variant="ghost" onPress={optOut} />
            </>
          )}
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
  icon: {
    fontSize: 48,
    lineHeight: 58,
  },
  center: {
    textAlign: 'center',
  },
  benefits: {
    alignSelf: 'stretch',
    gap: Spacing.two,
  },
  benefit: {
    flexDirection: 'row',
    gap: Spacing.two,
    alignItems: 'flex-start',
  },
  benefitText: {
    flex: 1,
  },
  free: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
    backgroundColor: '#FBA91E',
  },
  freeText: {
    color: '#1C2150',
    textAlign: 'center',
  },
  actions: {
    alignSelf: 'stretch',
    gap: Spacing.two,
  },
});
