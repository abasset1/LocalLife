import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';

import { ApiError, HttpError, deleteActivity, listMyContributions } from '@/api';
import type { Contribution } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing, type Theme } from '@/constants/theme';
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
        <ThemedText>Connectez-vous depuis la rubrique Compte du menu pour retrouver vos contributions.</ThemedText>
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
          <Notice tone="danger">Erreur : {error}</Notice>
        )}
        {contributions.length === 0 && !error && (
          <Card style={styles.empty}>
            <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
              ✍️
            </ThemedText>
            <ThemedText style={styles.emptyText}>Vous n’avez encore proposé aucune activité.</ThemedText>
            <Button label="Proposer une activité" onPress={() => router.navigate('/contribution')} />
          </Card>
        )}
        {contributions.map((contribution) => (
          <Card key={contribution.id} style={styles.card}>
            <View style={[styles.status, statusStyle(contribution.status, theme)]}>
              <ThemedText type="eyebrow" style={{ color: statusStyle(contribution.status, theme).color }}>
                {STATUS_LABELS[contribution.status] ?? contribution.status}
              </ThemedText>
            </View>
            <ThemedText type="heading">{contribution.title}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatWhen(contribution)}
              {contribution.city ? ` · ${contribution.city}` : ''}
            </ThemedText>
            <View style={styles.cardActions}>
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
              variant="danger"
              loading={busyId === contribution.id}
              onPress={() => void remove(contribution)}
            />
            </View>
          </Card>
        ))}
      </ScrollView>
    </ThemedView>
  );
}

/** Pastille de statut : orange en attente, vert publiée, rouge refusée ou annulée. */
function statusStyle(status: string, theme: Theme): { backgroundColor: string; color: string } {
  if (status === 'PENDING') return { backgroundColor: theme.warningSoft, color: theme.warning };
  if (status === 'PUBLISHED') return { backgroundColor: theme.successSoft, color: theme.success };
  if (status === 'REJECTED' || status === 'CANCELLED') return { backgroundColor: theme.dangerSoft, color: theme.danger };
  return { backgroundColor: theme.backgroundSelected, color: theme.textSecondary };
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
  },
  status: {
    alignSelf: 'flex-start',
    paddingHorizontal: Spacing.two + Spacing.one,
    paddingVertical: Spacing.one,
    borderRadius: Radius.pill,
  },
  cardActions: {
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  empty: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  emptyIcon: {
    fontSize: 40,
    lineHeight: 48,
    textAlign: 'center',
  },
  emptyText: {
    textAlign: 'center',
  },
});
