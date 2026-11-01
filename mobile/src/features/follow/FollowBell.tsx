import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet } from 'react-native';

import type { FollowTargetType } from '@/api';
import { useAuth } from '@/auth';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { useFollows } from './FollowsProvider';

/**
 * Cloche « Suivre » : être prévenu des mises à jour d'une activité (dates, lieu, annulation…) ou d'un lieu (nouvelles
 * activités, adresse…). Réservée aux offres Premium et Pro : un compte gratuit se voit proposer les offres ; un
 * visiteur, la connexion.
 */
export function FollowBell({
  type,
  id,
  title,
  city = null,
}: {
  type: FollowTargetType;
  id: number;
  title: string;
  city?: string | null;
}) {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { isFollowing, setFollowing } = useFollows();
  const [busy, setBusy] = useState(false);
  const following = isFollowing(type, id);
  const what = type === 'activity' ? 'cette activité' : 'ce lieu';

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
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={following ? `Ne plus suivre ${what}` : `Suivre ${what}`}
      accessibilityHint={following ? undefined : 'Être prévenu de ses mises à jour (Premium et Pro)'}
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
