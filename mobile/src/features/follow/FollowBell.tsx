import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet } from 'react-native';

import type { FollowTargetType } from '@/api';
import { useAuth } from '@/auth';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLaunchFeatures } from '@/features/launch';
import { enablePushNotifications } from '@/notifications';

import { followPushNotice } from './follows';
import { useFollows } from './FollowsProvider';

const WHAT: Record<FollowTargetType, string> = {
  activity: 'cette activité',
  place: 'ce lieu',
  foodtruck: 'ce food truck',
};

/**
 * Cloche « Suivre » : être prévenu des mises à jour d'une activité (dates, lieu, annulation…), d'un lieu (nouvelles
 * activités, adresse…) ou d'un food truck (planning, profil, et le jour où il est quelque part). Réservée aux offres Premium et Pro : un compte gratuit se voit proposer les offres ; un
 * visiteur, la connexion.
 */
interface FollowBellProps {
  type: FollowTargetType;
  id: number;
  title: string;
  city?: string | null;
}

/** Refus des notifications déjà signalé pendant cette session (voir `followPushNotice`). */
let pushDeniedNoticed = false;

/** Après un nouveau suivi : demande la permission de notifier et enregistre l'appareil, sans bloquer le suivi. */
async function enablePushAfterFollow(): Promise<void> {
  const notice = followPushNotice(await enablePushNotifications(), pushDeniedNoticed);
  if (notice) {
    pushDeniedNoticed = true;
    Alert.alert('Notifications désactivées', notice);
  }
}

/** Masquée tant que Premium n'est pas ouvert (lancement progressif, 05/11/2026). */
export function FollowBell(props: FollowBellProps) {
  const { premium } = useLaunchFeatures();
  return premium ? <OpenFollowBell {...props} /> : null;
}

function OpenFollowBell({ type, id, title, city = null }: FollowBellProps) {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { isFollowing, setFollowing } = useFollows();
  const [busy, setBusy] = useState(false);
  const following = isFollowing(type, id);
  const what = WHAT[type];

  async function press() {
    if (state.status !== 'authenticated') {
      Alert.alert('Connexion requise', `Connectez-vous pour suivre ${what} et être prévenu de ses mises à jour.`, [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Se connecter', onPress: () => router.navigate('/compte') },
      ]);
      return;
    }
    setBusy(true);
    const problem = await setFollowing({ type, id, title, city }, !following);
    setBusy(false);
    if (problem?.planLimit) {
      Alert.alert('Offre Premium ou Pro', problem.message, [
        { text: 'Plus tard', style: 'cancel' },
        { text: 'Voir les offres', onPress: () => router.push('/abonnement') },
      ]);
    } else if (problem) {
      Alert.alert('Suivi non enregistré', problem.message);
    } else if (!following) {
      void enablePushAfterFollow();
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={following ? `Ne plus suivre ${what}` : `Suivre ${what}`}
      accessibilityHint={
        following
          ? undefined
          : type === 'foodtruck'
            ? 'Être prévenu de ses changements et le jour où il est quelque part (Premium et Pro)'
            : 'Être prévenu de ses mises à jour (Premium et Pro)'
      }
      accessibilityState={{ selected: following, busy }}
      disabled={busy}
      hitSlop={6}
      onPress={() => void press()}
      style={({ pressed }) => [
        styles.pill,
        following
          ? { backgroundColor: theme.primary, borderColor: theme.primary }
          : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        (pressed || busy) && styles.pressed,
      ]}>
      <ThemedText type="smallBold" style={{ color: following ? theme.onPrimary : theme.primary }}>
        🔔 {following ? 'Suivi ✓' : 'Suivre'}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: {
    alignSelf: 'flex-start',
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: 999,
    borderWidth: 1,
  },
  pressed: {
    opacity: 0.7,
  },
});
