import { useRouter } from 'expo-router';
import { Alert, Pressable, StyleSheet } from 'react-native';

import { useAuth } from '@/auth';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { useFavorites } from './FavoritesProvider';

/**
 * Cœur « J'aime » d'une activité. `floating` : pastille blanche ombrée, posée sur une photo ou un
 * bandeau. Pour un visiteur, propose de se connecter.
 */
export function LikeButton({
  activityId,
  title,
  floating = false,
}: {
  activityId: number;
  /** Titre de l'activité, pour le nom accessible. */
  title: string;
  floating?: boolean;
}) {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { isLiked, toggle } = useFavorites();
  const liked = isLiked(activityId);

  async function press() {
    if (state.status !== 'authenticated') {
      Alert.alert('Connexion requise', 'Connectez-vous pour enregistrer vos activités favorites.', [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Se connecter', onPress: () => router.navigate('/compte') },
      ]);
      return;
    }
    const problem = await toggle(activityId);
    if (problem) Alert.alert('Favori non enregistré', problem);
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={liked ? `Retirer « ${title} » des favoris` : `Ajouter « ${title} » aux favoris`}
      accessibilityState={{ selected: liked }}
      hitSlop={10}
      onPress={() => void press()}
      style={({ pressed }) => [
        styles.button,
        floating && [styles.floating, { backgroundColor: theme.backgroundElement }],
        pressed && styles.pressed,
      ]}>
      <ThemedText style={[styles.heart, { color: liked ? theme.accent : theme.textSecondary }]}>
        {liked ? '♥' : '♡'}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Radius.pill,
  },
  floating: {
    ...Shadows.floating,
  },
  heart: {
    fontSize: 22,
    lineHeight: 26,
    marginTop: Spacing.half,
  },
  pressed: {
    transform: [{ scale: 0.9 }],
  },
});
