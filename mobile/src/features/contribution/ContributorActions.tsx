import { useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiError, HttpError, deleteActivity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { confirmDestructive } from './confirm';
import { canManageActivity } from './permissions';

/**
 * « Modifier » / « Supprimer » sur la fiche d'une activité, affichés seulement à son créateur et aux
 * administrateurs. La modification passe par l'écran de « Mes contributions » ; après suppression,
 * `onDeleted` est appelé (retour à la carte).
 */
export function ContributorActions({
  activityId,
  title,
  createdBy,
  onDeleted,
}: {
  activityId: number;
  title: string;
  createdBy: number | null | undefined;
  onDeleted: () => void;
}) {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const user = state.status === 'authenticated' ? state.user : null;
  if (!canManageActivity(user, createdBy)) return null;

  async function remove() {
    const confirmed = await confirmDestructive(
      'Supprimer cette activité ?',
      `« ${title} » sera définitivement supprimée.`,
      'Supprimer',
    );
    if (!confirmed) return;
    setError(null);
    setDeleting(true);
    try {
      await deleteActivity(activityId);
      onDeleted();
    } catch (e) {
      if (e instanceof HttpError && e.isNotFound) {
        onDeleted();
        return;
      }
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
      setDeleting(false);
    }
  }

  return (
    <View style={styles.actions}>
      <Button
        label="Modifier"
        variant="secondary"
        disabled={deleting}
        onPress={() => router.push(`/contributions/${activityId}`)}
      />
      <Button label="Supprimer" variant="danger" loading={deleting} onPress={() => void remove()} />
      {error && (
        <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
          Erreur : {error}
        </ThemedText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  actions: {
    gap: Spacing.two,
  },
});
