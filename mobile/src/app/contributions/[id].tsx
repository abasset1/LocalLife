import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';

import { ApiError, getContribution, updateActivity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import {
  ContributionFields,
  fromContribution,
  inform,
  toRequest,
  useActiveCategories,
} from '@/features/contribution';
import type { ContributionForm } from '@/features/contribution';
import { useTheme } from '@/hooks/use-theme';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Modification d'une contribution, pré-remplie. Faite par le contributeur, elle repasse l'activité en
 * attente de validation : elle quitte la carte jusqu'à ce qu'un modérateur la valide de nouveau.
 */
export default function EditContributionScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { state } = useAuth();
  const { categories } = useActiveCategories();
  const [form, setForm] = useState<ContributionForm | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isAdmin = state.status === 'authenticated' && state.user.role === 'ADMIN';

  useEffect(() => {
    const controller = new AbortController();
    getContribution(id, { signal: controller.signal })
      .then((contribution) => setForm(fromContribution(contribution)))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setLoadError(messageFor(e));
      });
    return () => controller.abort();
  }, [id]);

  async function save() {
    if (!form) return;
    setError(null);
    const result = toRequest(form);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    setSaving(true);
    try {
      await updateActivity(Number(id), result.request);
      inform(
        'Modification enregistrée',
        isAdmin
          ? 'L’activité a été mise à jour.'
          : 'L’activité sera de nouveau visible sur la carte après validation par un modérateur.',
      );
      router.back();
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText accessibilityRole="alert">{loadError}</ThemedText>
        <Button label="Retour" onPress={() => router.back()} />
      </ThemedView>
    );
  }

  if (!form) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement de l’activité" />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          {!isAdmin && (
            <ThemedText style={[styles.notice, { backgroundColor: theme.backgroundElement }]}>
              Après modification, l’activité repasse en validation : elle ne sera de nouveau visible sur la
              carte qu’une fois validée par un modérateur.
            </ThemedText>
          )}
          <ContributionFields form={form} categories={categories} onChange={setForm} />
          {error && (
            <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
              Erreur : {error}
            </ThemedText>
          )}
          <Button label="Enregistrer les modifications" loading={saving} onPress={() => void save()} />
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
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
    gap: Spacing.three,
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  notice: {
    padding: Spacing.three,
    borderRadius: 8,
  },
});
