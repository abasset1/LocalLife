import { useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { ActivityIndicator, StyleSheet } from 'react-native';

import { ApiError, HttpError } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/auth';

import { PLAN_DESCRIPTIONS } from './format';

/** Message affichable pour une erreur : celui de l'API, sinon un message générique. */
export function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/** L'espace professionnel a refusé l'accès (pas d'offre Pro). */
export function isProDenied(error: unknown): boolean {
  return error instanceof HttpError && error.isForbidden;
}

/**
 * Garde des écrans de l'espace professionnel : connexion exigée, puis, si le backend refuse l'accès (403, pas
 * d'offre Pro), invitation à passer à l'offre Pro. Le droit est toujours vérifié par le backend.
 */
export function ProGate({ loading, denied, children }: { loading: boolean; denied: boolean; children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  if (state.status !== 'authenticated') {
    return (
      <ThemedView style={proStyles.centered}>
        <ThemedText style={proStyles.centerText}>
          Connectez-vous depuis la rubrique Compte du menu pour accéder à l’espace professionnel.
        </ThemedText>
        <Button label="Se connecter" onPress={() => router.navigate('/compte')} />
      </ThemedView>
    );
  }
  if (denied) {
    return (
      <ThemedView style={proStyles.centered}>
        <Card style={proStyles.card}>
          <ThemedText type="subtitle">Offre Pro</ThemedText>
          <ThemedText themeColor="textSecondary">{PLAN_DESCRIPTIONS.PRO}</ThemedText>
          <Button label="Passer à l’offre Pro" onPress={() => router.push('/abonnement')} />
        </Card>
      </ThemedView>
    );
  }
  if (loading) {
    return (
      <ThemedView style={proStyles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement" />
      </ThemedView>
    );
  }
  return <>{children}</>;
}

export const proStyles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    gap: Spacing.three,
  },
  centerText: {
    textAlign: 'center',
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  card: {
    gap: Spacing.two,
  },
  form: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
});
