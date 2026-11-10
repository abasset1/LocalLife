import type { ErrorBoundaryProps } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';

/**
 * Filet de sécurité de l'application (`ErrorBoundary` d'expo-router) : une erreur d'affichage, par exemple une donnée
 * inattendue renvoyée par l'API, affiche ce message au lieu de fermer l'application. Composants React Native seuls,
 * sans les fournisseurs de l'application, qui peuvent être eux-mêmes en cause.
 */
export function ScreenError({ error, retry }: ErrorBoundaryProps) {
  const colors = Colors.light;
  if (__DEV__) console.error(error);
  return (
    <View style={[styles.container, { backgroundColor: colors.background }]} accessibilityRole="alert">
      <Text style={[styles.title, { color: colors.text }]}>Oups, un problème d’affichage</Text>
      <Text style={[styles.text, { color: colors.textSecondary }]}>
        Cet écran n’a pas pu s’afficher. Réessayez ; si le problème continue, signalez-le depuis le menu.
      </Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => void retry()}
        style={({ pressed }) => [styles.button, { backgroundColor: colors.primary }, pressed && styles.pressed]}>
        <Text style={[styles.buttonLabel, { color: colors.onPrimary }]}>Réessayer</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    gap: Spacing.three,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
  },
  text: {
    fontSize: 15,
    textAlign: 'center',
  },
  button: {
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: Spacing.four,
    borderRadius: Radius.pill,
  },
  buttonLabel: {
    fontSize: 16,
    fontWeight: '700',
  },
  pressed: {
    opacity: 0.7,
  },
});
