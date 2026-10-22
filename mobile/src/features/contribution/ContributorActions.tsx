import { useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiError, HttpError, cancelActivity, deleteActivity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import { confirmDestructive } from './confirm';
import { canCancelActivity, canManageActivity, requiresModerationReason } from './permissions';

/** Action en cours de saisie de motif. */
type PendingAction = 'cancel' | 'delete';

/**
 * « Modifier » / « Annuler l'activité » / « Supprimer » sur la fiche d'une activité, affichés seulement à son
 * créateur et aux administrateurs. La modification passe par l'écran de « Mes contributions ». Annuler retire
 * l'activité de la carte en la gardant (statut « annulée ») ; après annulation, `onCancelled` est appelé pour
 * recharger la fiche. Après suppression, `onDeleted` est appelé (retour à la carte). Un administrateur qui annule ou
 * supprime la contribution d'un autre saisit un motif, communiqué à l'auteur ; une suppression part 30 jours à la
 * corbeille.
 */
export function ContributorActions({
  activityId,
  title,
  createdBy,
  status,
  onDeleted,
  onCancelled,
}: {
  activityId: number;
  title: string;
  createdBy: number | null | undefined;
  /** Statut de l'activité ; sans lui, l'annulation n'est pas proposée. */
  status?: string | null;
  onDeleted: () => void;
  onCancelled?: () => void;
}) {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const [busy, setBusy] = useState<PendingAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** Action dont le motif est en cours de saisie, et ce motif. */
  const [pending, setPending] = useState<{ action: PendingAction; reason: string } | null>(null);

  const user = state.status === 'authenticated' ? state.user : null;
  const manageable = canManageActivity(user, createdBy);
  // Rôle relu par le serveur (`GET /users/me`), plutôt que celui du jeton, pour exiger un motif.
  const serverRole = useCurrentAccount(manageable)?.role ?? null;
  if (!manageable) return null;
  const needsReason = requiresModerationReason(user, createdBy, serverRole);
  const cancellable = canCancelActivity(status) && onCancelled !== undefined;

  async function run(action: PendingAction) {
    const reason = pending?.action === action ? pending.reason.trim() : '';
    if (needsReason && reason === '') {
      setPending({ action, reason: '' });
      return;
    }
    const confirmed =
      action === 'cancel'
        ? await confirmDestructive(
            'Annuler cette activité ?',
            `« ${title} » ne sera plus affichée sur la carte. Elle reste visible dans les contributions de son auteur.`,
            "Annuler l'activité",
          )
        : await confirmDestructive(
            'Supprimer cette activité ?',
            needsReason
              ? `« ${title} » sera placée dans la corbeille pendant 30 jours, puis supprimée.`
              : `« ${title} » sera définitivement supprimée.`,
            'Supprimer',
          );
    if (!confirmed) return;
    setError(null);
    setBusy(action);
    const options = needsReason ? { reason } : {};
    try {
      if (action === 'cancel') {
        await cancelActivity(activityId, options);
        setPending(null);
        setBusy(null);
        onCancelled?.();
      } else {
        await deleteActivity(activityId, options);
        onDeleted();
      }
    } catch (e) {
      if (action === 'delete' && e instanceof HttpError && e.isNotFound) {
        onDeleted();
        return;
      }
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
      setBusy(null);
    }
  }

  const label = (action: PendingAction) => (action === 'cancel' ? "Annuler l'activité" : 'Supprimer');

  return (
    <View style={styles.actions}>
      <Button
        label="Modifier"
        variant="secondary"
        disabled={busy !== null}
        onPress={() => router.push(`/contributions/${activityId}`)}
      />
      {pending !== null ? (
        <>
          <TextField
            label={
              pending.action === 'cancel'
                ? 'Motif de l’annulation (communiqué à l’auteur)'
                : 'Motif de la suppression (communiqué à l’auteur)'
            }
            value={pending.reason}
            onChangeText={(reason) => setPending({ action: pending.action, reason })}
            multiline
            maxLength={1000}
          />
          <Button
            label={`${label(pending.action)} avec ce motif`}
            variant="danger"
            loading={busy === pending.action}
            disabled={pending.reason.trim() === ''}
            onPress={() => void run(pending.action)}
          />
          <Button label="Retour" variant="ghost" disabled={busy !== null} onPress={() => setPending(null)} />
        </>
      ) : (
        <>
          {cancellable && (
            <Button
              label="Annuler l'activité"
              variant="secondary"
              loading={busy === 'cancel'}
              disabled={busy !== null}
              onPress={() => void run('cancel')}
            />
          )}
          <Button
            label="Supprimer"
            variant="danger"
            loading={busy === 'delete'}
            disabled={busy !== null}
            onPress={() => void run('delete')}
          />
        </>
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
