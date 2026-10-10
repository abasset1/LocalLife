import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';

import { ApiError, HttpError, deleteActivity, listMyContributions } from '@/api';
import type { Contribution } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { STATUS_LABELS, confirmDestructive, formatWhen, isEditable } from '@/features/contribution';
import { useTheme } from '@/hooks/use-theme';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * « Mes contributions » : toutes les activités proposées, quel que soit leur statut (y compris en
 * attente de validation ou refusées, invisibles sur la carte), avec « Modifier » et « Supprimer ».
 */
export default function MyContributionsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const [contributions, setContributions] = useState<Contribution[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const authenticated = state.status === 'authenticated';
  // Rechargée à chaque retour sur l'écran : une modification change le statut.
  useFocusEffect(
    useCallback(() => {
      if (!authenticated) return undefined;
      const controller = new AbortController();
      listMyContributions({ signal: controller.signal })
        .then((list) => {
          setContributions(list);
          setError(null);
          setLoading(false);
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          setError(messageFor(e));
          setLoading(false);
        });
      return () => controller.abort();
    }, [authenticated]),
  );

  async function remove(contribution: Contribution) {
    const confirmed = await confirmDestructive(
      'Supprimer cette activité ?',
      `« ${contribution.title} » sera définitivement supprimée.`,
      'Supprimer',
    );
    if (!confirmed) return;
    setError(null);
    setBusyId(contribution.id);
    try {
      await deleteActivity(contribution.id);
      setContributions((current) => current.filter((c) => c.id !== contribution.id));
    } catch (e) {
      if (e instanceof HttpError && e.isNotFound) {
        setContributions((current) => current.filter((c) => c.id !== contribution.id));
      } else {
        setError(messageFor(e));
      }
    } finally {
      setBusyId(null);
    }
  }

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText>Connectez-vous depuis l’onglet Compte pour retrouver vos contributions.</ThemedText>
      </ThemedView>
    );
  }

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement de vos contributions" />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        {error && (
          <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
            Erreur : {error}
          </ThemedText>
        )}
        {contributions.length === 0 && !error && (
          <ThemedText>Vous n’avez encore proposé aucune activité.</ThemedText>
        )}
        {contributions.map((contribution) => (
          <View key={contribution.id} style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="smallBold">{contribution.title}</ThemedText>
            <ThemedText type="small" style={{ color: statusColor(contribution.status, theme.danger) }}>
              {STATUS_LABELS[contribution.status] ?? contribution.status}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatWhen(contribution)}
              {contribution.city ? ` · ${contribution.city}` : ''}
            </ThemedText>
            {isEditable(contribution.status) && (
              <Button
                label="Modifier"
                variant="secondary"
                disabled={busyId === contribution.id}
                onPress={() => router.push(`/contributions/${contribution.id}`)}
              />
            )}
            <Button
              label="Supprimer"
              variant="secondary"
              loading={busyId === contribution.id}
              onPress={() => void remove(contribution)}
            />
          </View>
        ))}
      </ScrollView>
    </ThemedView>
  );
}

function statusColor(status: string, danger: string): string | undefined {
  if (status === 'PENDING') return '#B45309';
  if (status === 'PUBLISHED') return '#15803D';
  if (status === 'REJECTED' || status === 'CANCELLED') return danger;
  return undefined;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
  },
  content: {
    gap: Spacing.three,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  card: {
    gap: Spacing.two,
    padding: Spacing.three,
    borderRadius: 12,
  },
});
