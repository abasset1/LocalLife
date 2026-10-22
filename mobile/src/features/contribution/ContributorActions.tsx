import { useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiError, HttpError, deleteActivity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import { confirmDestructive } from './confirm';
import { canManageActivity, requiresModerationReason } from './permissions';

/**
 * « Modifier » / « Supprimer » sur la fiche d'une activité, affichés seulement à son créateur et aux
 * administrateurs. La modification passe par l'écran de « Mes contributions » ; après suppression,
 * `onDeleted` est appelé (retour à la carte). Un administrateur qui supprime la contribution d'un autre
 * saisit un motif : l'activité part 30 jours à la corbeille.
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
  /** Motif saisi par un administrateur ; `null` tant que le champ n'est pas ouvert. */
  const [reason, setReason] = useState<string | null>(null);

  const user = state.status === 'authenticated' ? state.user : null;
  const manageable = canManageActivity(user, createdBy);
  // Rôle relu par le serveur (`GET /users/me`), plutôt que celui du jeton, pour exiger un motif.
  const serverRole = useCurrentAccount(manageable)?.role ?? null;
  if (!manageable) return null;
  const needsReason = requiresModerationReason(user, createdBy, serverRole);

  async function remove() {
    const trimmed = reason?.trim() ?? '';
    if (needsReason && trimmed === '') {
      setReason('');
      return;
    }
    const confirmed = await confirmDestructive(
      'Supprimer cette activité ?',
      needsReason
        ? `« ${title} » sera placée dans la corbeille pendant 30 jours, puis supprimée.`
        : `« ${title} » sera définitivement supprimée.`,
      'Supprimer',
    );
    if (!confirmed) return;
    setError(null);
    setDeleting(true);
    try {
      await deleteActivity(activityId, needsReason ? { reason: trimmed } : {});
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
      {needsReason && reason !== null ? (
        <>
          <TextField
            label="Motif de la suppression (communiqué à l’auteur)"
            value={reason}
            onChangeText={setReason}
            multiline
            maxLength={1000}
          />
          <Button
            label="Supprimer avec ce motif"
            variant="danger"
            loading={deleting}
            disabled={reason.trim() === ''}
            onPress={() => void remove()}
          />
          <Button label="Annuler" variant="ghost" disabled={deleting} onPress={() => setReason(null)} />
        </>
      ) : (
        <Button label="Supprimer" variant="danger" loading={deleting} onPress={() => void remove()} />
      )}
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
