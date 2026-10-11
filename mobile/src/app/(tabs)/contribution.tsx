import { useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, createActivity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { ContributionFields, EMPTY_FORM, inform, toRequest, useActiveCategories } from '@/features/contribution';
import type { ContributionForm } from '@/features/contribution';
import { useTheme } from '@/hooks/use-theme';

/**
 * Onglet « Contribution » : proposer une activité, ponctuelle ou qui revient chaque semaine (food
 * truck, marché…). Elle est créée en attente de validation par un modérateur. « Mes contributions »
 * permet ensuite de la modifier ou de la supprimer.
 */
export default function ContributionScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { categories, error: categoriesError } = useActiveCategories();
  const [form, setForm] = useState<ContributionForm>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  async function submit() {
    setError(null);
    setSubmitted(false);
    const result = toRequest(form);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    setSubmitting(true);
    try {
      await createActivity(result.request);
      setForm(EMPTY_FORM);
      setSubmitted(true);
      inform(
        'Merci pour votre proposition',
        'Elle est soumise à validation par un modérateur et sera publiée d’ici 2 à 3 jours. Suivez son statut dans « Mes contributions ».',
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
    } finally {
      setSubmitting(false);
    }
  }

  if (state.status !== 'authenticated') {
    return (
      <ThemedView style={styles.container}>
        <SafeAreaView style={styles.centered}>
          <ThemedText type="title">Contribution</ThemedText>
          <ThemedText themeColor="textSecondary">
            Connectez-vous depuis l’onglet Compte pour proposer une activité.
          </ThemedText>
        </SafeAreaView>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top']}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <ThemedText type="title">Proposer une activité</ThemedText>
            <Button label="Mes contributions" variant="secondary" onPress={() => router.push('/contributions')} />
            <ThemedText style={[styles.notice, { backgroundColor: theme.backgroundElement }]}>
              ℹ️ Votre activité sera soumise à validation par un modérateur avant d’apparaître sur la carte : elle
              sera publiée d’ici 2 à 3 jours.
            </ThemedText>
            {categoriesError && (
              <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
                Erreur : {categoriesError}
              </ThemedText>
            )}

            <ContributionFields
              form={form}
              categories={categories}
              onChange={(next) => {
                setForm(next);
                setSubmitted(false);
              }}
            />

            {error && (
              <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
                Erreur : {error}
              </ThemedText>
            )}
            {submitted && (
              <ThemedText accessibilityLiveRegion="polite">
                ✓ Activité proposée. Elle est soumise à validation et sera publiée d’ici 2 à 3 jours.
              </ThemedText>
            )}
            <Button label="Proposer l'activité" loading={submitting} onPress={() => void submit()} />
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.six,
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
