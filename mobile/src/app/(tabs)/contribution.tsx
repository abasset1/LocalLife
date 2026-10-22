import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, createActivity, getCurrentAccount } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ScreenHeader } from '@/components/screen-header';
import { MenuButton } from '@/features/navigation/AppMenu';
import { useHostablePlaces } from '@/features/pro/useProAccess';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { ContributionFields, EMPTY_FORM, inform, toRequest, useActiveCategories } from '@/features/contribution';
import type { ContributionForm } from '@/features/contribution';

/**
 * Onglet « Contribution » : proposer une activité, ponctuelle ou qui revient chaque semaine (food
 * truck, marché…). Elle est créée en attente de validation par un modérateur. « Mes contributions »
 * permet ensuite de la modifier ou de la supprimer.
 */
export default function ContributionScreen() {
  const router = useRouter();
  const { state } = useAuth();
  const { categories, error: categoriesError } = useActiveCategories();
  const [form, setForm] = useState<ContributionForm>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  /** Droit relu par le serveur (offre Premium ou Pro) ; `null` tant qu'il n'est pas connu. */
  const [access, setAccess] = useState<{ userId: number; canContribute: boolean; hasProAccess: boolean } | null>(
    null,
  );
  const authenticatedUserId = state.status === 'authenticated' ? state.user.userId : null;
  const currentAccess = access && access.userId === authenticatedUserId ? access : null;
  const canContribute = currentAccess ? currentAccess.canContribute : null;
  // Accès pro : l'activité peut être « proposée par » un des lieux publiés que le compte gère.
  const hostPlaces = useHostablePlaces(currentAccess?.hasProAccess === true);

  useEffect(() => {
    if (authenticatedUserId === null) return;
    const controller = new AbortController();
    getCurrentAccount({ signal: controller.signal })
      // Champ absent (ancien serveur) : le formulaire reste ouvert, le serveur tranchera à l'envoi.
      .then((account) =>
        setAccess({
          userId: authenticatedUserId,
          canContribute: account.canContribute !== false,
          hasProAccess: account.hasProAccess === true,
        }),
      )
      .catch(() => setAccess({ userId: authenticatedUserId, canContribute: true, hasProAccess: false }));
    return () => controller.abort();
  }, [authenticatedUserId]);

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
          <MenuButton />
          <ScreenHeader
            eyebrow="Contribuer"
            title="Faites vivre votre quartier"
            lead="Un marché, un concert, un food truck… Partagez ce qui se passe près de chez vous."
          />
          <Card style={styles.emptyCard}>
            <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
              ✍️
            </ThemedText>
            <ThemedText type="heading" style={styles.emptyText}>
              Connexion requise
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.emptyText}>
              Connectez-vous depuis la rubrique Compte du menu pour proposer une activité.
            </ThemedText>
            <Button label="Aller à mon compte" onPress={() => router.navigate('/compte')} />
          </Card>
        </SafeAreaView>
      </ThemedView>
    );
  }

  // 21/10/2026 : proposer une activité est réservé aux offres Premium et Pro.
  if (canContribute === false) {
    return (
      <ThemedView style={styles.container}>
        <SafeAreaView style={styles.centered}>
          <MenuButton />
          <ScreenHeader
            eyebrow="Contribuer"
            title="Faites vivre votre quartier"
            lead="Un marché, un concert, un food truck… Partagez ce qui se passe près de chez vous."
          />
          <Card style={styles.emptyCard}>
            <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
              ⭐
            </ThemedText>
            <ThemedText type="heading" style={styles.emptyText}>
              Réservé aux offres Premium et Pro
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.emptyText}>
              Passez à Premium pour proposer des activités. Vous gardez la main sur vos contributions existantes depuis
              « Mes contributions ».
            </ThemedText>
            <Button label="Voir les offres" onPress={() => router.push('/abonnement')} />
          </Card>
        </SafeAreaView>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <MenuButton />
            <ScreenHeader
              eyebrow="Contribuer"
              title="Proposer une activité"
              lead="Partagez un événement ou un rendez-vous régulier avec la communauté."
            />
            <Button
              label="Mes contributions"
              variant="secondary"
              icon={{ ios: 'square.and.pencil', android: 'edit_note', web: 'edit_note' }}
              onPress={() => router.push('/contributions')}
            />
            <Notice>
              Votre activité sera soumise à validation par un modérateur avant d’apparaître sur la carte : elle
              sera publiée d’ici 2 à 3 jours.
            </Notice>
            {categoriesError && <Notice tone="danger">Erreur : {categoriesError}</Notice>}

            <Card style={styles.form}>
              <ContributionFields
                form={form}
                categories={categories}
                hostPlaces={hostPlaces}
                onChange={(next) => {
                  setForm(next);
                  setSubmitted(false);
                }}
              />
            </Card>

            {error && <Notice tone="danger">Erreur : {error}</Notice>}
            {submitted && (
              <Notice tone="success">
                Activité proposée. Elle est soumise à validation et sera publiée d’ici 2 à 3 jours.
              </Notice>
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
    padding: Spacing.four,
    gap: Spacing.four,
  },
  content: {
    gap: Spacing.three,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  form: {
    gap: Spacing.four - Spacing.one,
    padding: Spacing.four - Spacing.one,
  },
  emptyCard: {
    alignItems: 'stretch',
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
