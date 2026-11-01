import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import { loadFollowNewsDismissed, saveFollowNewsDismissed, shouldShowFollowNews } from './followNews';

/**
 * Bandeau posé sur la carte pour les membres gratuits : annonce la cloche « Suivre » et Premium gratuit pour le
 * moment, et ouvre l'écran « Nouveautés ». Jamais affiché à un visiteur ni à un membre Premium ou Pro. Fermé (✕ ou
 * « Découvrir »), il ne revient plus pour ce compte sur cet appareil.
 */
export function FollowNewsBanner() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const canFollow = useCurrentAccount(userId !== null)?.limits?.canFollow;
  const [dismissed, setDismissed] = useState<{ userId: number; value: boolean } | null>(null);

  useEffect(() => {
    if (userId === null) return;
    let current = true;
    void loadFollowNewsDismissed(userId).then((value) => {
      if (current) setDismissed({ userId, value });
    });
    return () => {
      current = false;
    };
  }, [userId]);

  const dismissedForUser = dismissed && dismissed.userId === userId ? dismissed.value : null;
  if (!shouldShowFollowNews(userId, canFollow, dismissedForUser) || userId === null) return null;

  function close(openNews: boolean) {
    if (userId === null) return;
    setDismissed({ userId, value: true });
    void saveFollowNewsDismissed(userId);
    if (openNews) router.push('/nouveautes');
  }

  return (
    <View
      accessibilityLabel="Nouveauté"
      style={[styles.banner, { backgroundColor: theme.accentSoft, borderColor: theme.accent }]}>
      <ThemedText style={styles.bell} importantForAccessibility="no">
        🔔
      </ThemedText>
      <View style={styles.text}>
        <ThemedText type="smallBold">Nouveau : suivez vos activités et lieux préférés.</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          Premium est gratuit pour le moment.
        </ThemedText>
      </View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Découvrir la nouveauté"
        onPress={() => close(true)}
        style={({ pressed }) => [styles.discover, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
        <ThemedText type="smallBold" style={styles.discoverLabel}>
          Découvrir
        </ThemedText>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Fermer le bandeau"
        hitSlop={10}
        onPress={() => close(false)}
        style={styles.close}>
        <ThemedText themeColor="textSecondary">✕</ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.two,
    paddingLeft: Spacing.three,
    paddingRight: Spacing.one,
    borderRadius: Radius.lg,
    borderWidth: 1,
    ...Shadows.floating,
  },
  bell: {
    fontSize: 20,
    lineHeight: 26,
  },
  text: {
    flex: 1,
  },
  discover: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: 999,
  },
  discoverLabel: {
    color: '#FFFFFF',
  },
  close: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.8,
  },
});
