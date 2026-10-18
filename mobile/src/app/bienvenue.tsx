import { useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { BackHandler, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, createAlert, listCategories, updatePreferences } from '@/api';
import type { Category, UserPreferences } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BrandMark } from '@/components/brand-mark';
import { Notice } from '@/components/notice';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useAuth } from '@/auth';
import { alertError, preferencesError } from '@/features/onboarding';
import {
  ChannelSwitch,
  ChipGroup,
  CityPicker,
  DEFAULT_ALERT_RADIUS_KM,
  FOOD_TRUCKS_CHIP,
  RADIUS_OPTIONS_KM,
  toggleCode,
  useCityPicker,
} from '@/features/settings';
import { enablePushOrExplain } from '@/notifications';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Accueil de première connexion (ouvert par `OnboardingGate` quand le compte
 * n'a jamais enregistré ses préférences), même parcours que le site web :
 * 1. préférences obligatoires — ville, rayon, au moins une catégorie ;
 *    l'écran ne se ferme pas (pas de geste retour, bouton retour Android
 *    bloqué), seule la déconnexion permet d'en sortir sans enregistrer ;
 * 2. alerte facultative, préremplie avec ces préférences (notification sur
 *    ce téléphone par défaut, e-mail en option), ou « Plus tard ».
 */
export default function BienvenueScreen() {
  const router = useRouter();
  const { state, logout } = useAuth();
  const [step, setStep] = useState<'preferences' | 'alert'>('preferences');
  const [categories, setCategories] = useState<Category[]>([]);
  const [saved, setSaved] = useState<UserPreferences | null>(null);

  // Étape 1
  const [saveError, setSaveError] = useState<string | null>(null);
  const clearSaveError = useCallback(() => setSaveError(null), []);
  const cityPicker = useCityPicker(clearSaveError);
  const [radius, setRadius] = useState(String(DEFAULT_ALERT_RADIUS_KM));
  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  // Étape 2
  const [alertCodes, setAlertCodes] = useState<string[]>([]);
  const [includeFoodTrucks, setIncludeFoodTrucks] = useState(false);
  const [notifyPush, setNotifyPush] = useState(true);
  const [notifyEmail, setNotifyEmail] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [created, setCreated] = useState(false);

  // Bouton retour Android : sans effet tant que l'accueil est affiché.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => subscription.remove();
  }, []);

  // Session terminée (déconnexion, jeton expiré) : retour à l'application.
  const authenticated = state.status === 'authenticated';
  useEffect(() => {
    if (!authenticated) router.replace('/');
  }, [authenticated, router]);

  useEffect(() => {
    const controller = new AbortController();
    listCategories({ signal: controller.signal })
      .then((all) =>
        setCategories(all.filter((c) => c.active).sort((a, b) => a.label.localeCompare(b.label, 'fr'))),
      )
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  const finish = useCallback(() => router.replace('/'), [router]);

  async function savePreferences() {
    setSaveError(null);
    const problem = preferencesError(cityPicker.city, categoryCodes);
    if (problem) {
      setSaveError(problem);
      return;
    }
    setSaving(true);
    try {
      const preferences = await updatePreferences({
        defaultCity: cityPicker.city.trim(),
        defaultLatitude: cityPicker.position?.latitude ?? null,
        defaultLongitude: cityPicker.position?.longitude ?? null,
        defaultRadiusKm: Number(radius),
        defaultCategoryCodes: categoryCodes,
      });
      setSaved(preferences);
      setAlertCodes(preferences.defaultCategoryCodes);
      setStep('alert');
    } catch (e) {
      setSaveError(messageFor(e));
    } finally {
      setSaving(false);
    }
  }

  async function createFirstAlert() {
    if (!saved) return;
    setCreateError(null);
    const problem = alertError(alertCodes, includeFoodTrucks, notifyPush, notifyEmail);
    if (problem) {
      setCreateError(problem);
      return;
    }
    setCreating(true);
    try {
      if (notifyPush) {
        const pushProblem = await enablePushOrExplain();
        if (pushProblem) {
          setCreateError(pushProblem);
          return;
        }
      }
      await createAlert({
        categoryCodes: alertCodes,
        includeFoodTrucks,
        city: saved.defaultCity,
        latitude: null,
        longitude: null,
        radiusKm: saved.defaultRadiusKm ?? DEFAULT_ALERT_RADIUS_KM,
        notifyEmail,
        notifyPush,
      });
      setCreated(true);
    } catch (e) {
      setCreateError(messageFor(e));
    } finally {
      setCreating(false);
    }
  }

  const categoryOptions = categories.map((c) => ({ value: c.code, label: c.label }));

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
        <View style={styles.header}>
          <BrandMark inverted />
          <View style={styles.progress} importantForAccessibility="no">
            <View style={[styles.progressStep, styles.progressDone]} />
            <View style={[styles.progressStep, step === 'alert' && styles.progressDone]} />
          </View>
          <ThemedText type="eyebrow" style={styles.headerStep}>
            Étape {step === 'preferences' ? 1 : 2} sur 2
          </ThemedText>
          <ThemedText type="subtitle" accessibilityRole="header" style={styles.headerTitle}>
            {step === 'preferences' ? 'Bienvenue sur LocalLife 👋' : 'Créer une alerte ?'}
          </ThemedText>
        </View>

        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            {step === 'preferences' && (
              <>
                <ThemedText themeColor="textSecondary">
                  Pour commencer, dites-nous où et quoi chercher : la carte s’ouvrira toujours sur ces préférences.
                  Vous pourrez les modifier à tout moment depuis la rubrique Compte du menu.
                </ThemedText>
                <CityPicker label="Votre ville" picker={cityPicker} />
                <ChipGroup
                  label="Rayon de recherche"
                  single
                  options={RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` }))}
                  selected={[radius]}
                  onToggle={(value) => {
                    setRadius(value);
                    setSaveError(null);
                  }}
                />
                <ChipGroup
                  label="Ce qui vous intéresse (au moins une catégorie)"
                  options={categoryOptions}
                  selected={categoryCodes}
                  onToggle={(code) => {
                    setCategoryCodes((current) => toggleCode(current, code));
                    setSaveError(null);
                  }}
                />
                {saveError && (
                  <Notice tone="danger">{saveError}</Notice>
                )}
                <Button
                  label="Continuer"
                  loading={saving}
                  disabled={cityPicker.blocksSubmit}
                  onPress={() => void savePreferences()}
                />
                <Pressable
                  accessibilityRole="button"
                  onPress={() => void logout()}
                  style={styles.logout}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.logoutLabel}>
                    Se déconnecter
                  </ThemedText>
                </Pressable>
              </>
            )}

            {step === 'alert' && saved && (
              <>
                {created ? (
                  <Notice tone="success">
                    C’est prêt ! Vous serez prévenu chaque matin des nouveautés à moins de {saved.defaultRadiusKm}{' '}
                    km de {saved.defaultCity}.
                  </Notice>
                ) : (
                  <>
                    <ThemedText themeColor="textSecondary">
                      Soyez prévenu chaque matin des nouveautés à moins de {saved.defaultRadiusKm} km de{' '}
                      {saved.defaultCity}. C’est facultatif, et vous pourrez gérer vos alertes depuis la
                      rubrique Compte du menu.
                    </ThemedText>
                    <ChipGroup
                      label="M’alerter pour"
                      options={[...categoryOptions, { value: FOOD_TRUCKS_CHIP, label: '🚚 Food trucks' }]}
                      selected={includeFoodTrucks ? [...alertCodes, FOOD_TRUCKS_CHIP] : alertCodes}
                      onToggle={(value) => {
                        if (value === FOOD_TRUCKS_CHIP) setIncludeFoodTrucks((current) => !current);
                        else setAlertCodes((current) => toggleCode(current, value));
                        setCreateError(null);
                      }}
                    />
                    <ChannelSwitch
                      label="Notification sur ce téléphone"
                      value={notifyPush}
                      onValueChange={(value) => {
                        setNotifyPush(value);
                        setCreateError(null);
                      }}
                    />
                    <ChannelSwitch
                      label="E-mail"
                      value={notifyEmail}
                      onValueChange={(value) => {
                        setNotifyEmail(value);
                        setCreateError(null);
                      }}
                    />
                    {createError && (
                      <Notice tone="danger">{createError}</Notice>
                    )}
                  </>
                )}
                {created ? (
                  <Button label="Découvrir la carte" onPress={finish} />
                ) : (
                  <>
                    <Button label="Créer l’alerte" loading={creating} onPress={() => void createFirstAlert()} />
                    <Button label="Plus tard" variant="secondary" disabled={creating} onPress={finish} />
                  </>
                )}
              </>
            )}
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
  header: {
    gap: Spacing.two,
    marginHorizontal: Spacing.three,
    marginTop: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.four,
    borderRadius: Radius.xl,
    backgroundColor: Brand.green,
  },
  progress: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  progressStep: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.25)',
  },
  progressDone: {
    backgroundColor: Brand.coral,
  },
  headerStep: {
    color: '#C8D7D1',
    marginTop: Spacing.one,
  },
  headerTitle: {
    color: '#ffffff',
    fontSize: 26,
    lineHeight: 32,
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  logout: {
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoutLabel: {
    textDecorationLine: 'underline',
  },
});
