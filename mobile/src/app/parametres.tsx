import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';

import { ApiError, getPreferences, listCategories, updatePreferences } from '@/api';
import type { Category } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/auth';
import { ChipGroup, CityPicker, RADIUS_OPTIONS_KM, toggleCode, useCityPicker } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

const DEFAULT_RADIUS = 'default';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Paramètres de recherche (partagés avec le site web) : ville, rayon et
 * catégories appliqués à l'ouverture de la carte « Découvrir ». Un champ
 * vide garde le comportement standard de la carte.
 */
export default function ParametresScreen() {
  const theme = useTheme();
  const { state } = useAuth();
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [radius, setRadius] = useState<string>(DEFAULT_RADIUS);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const onEdit = useCallback(() => setSaved(false), []);
  const cityPicker = useCityPicker(onEdit);
  const { setSavedCity } = cityPicker;

  const authenticated = state.status === 'authenticated';
  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    Promise.all([listCategories({ signal: controller.signal }), getPreferences({ signal: controller.signal })])
      .then(([allCategories, preferences]) => {
        setCategories(
          allCategories.filter((c) => c.active).sort((a, b) => a.label.localeCompare(b.label, 'fr')),
        );
        setCategoryCodes(preferences.defaultCategoryCodes);
        setRadius(preferences.defaultRadiusKm === null ? DEFAULT_RADIUS : String(preferences.defaultRadiusKm));
        setSavedCity(preferences.defaultCity);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setLoadError(messageFor(e));
        setLoading(false);
      });
    return () => controller.abort();
  }, [authenticated, setSavedCity]);

  async function save() {
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      const preferences = await updatePreferences({
        defaultCity: cityPicker.city.trim() || null,
        defaultLatitude: cityPicker.position?.latitude ?? null,
        defaultLongitude: cityPicker.position?.longitude ?? null,
        defaultRadiusKm: radius === DEFAULT_RADIUS ? null : Number(radius),
        defaultCategoryCodes: categoryCodes,
      });
      setSavedCity(preferences.defaultCity);
      setCategoryCodes(preferences.defaultCategoryCodes);
      setSaved(true);
    } catch (e) {
      setSaveError(messageFor(e));
    } finally {
      setSaving(false);
    }
  }

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText>Connectez-vous depuis l’onglet Compte pour choisir vos paramètres.</ThemedText>
      </ThemedView>
    );
  }

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement des paramètres" />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          {loadError ? (
            <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
              Erreur : {loadError}
            </ThemedText>
          ) : (
            <>
              <ThemedText themeColor="textSecondary">
                Appliqués à l’ouverture de la carte. Laissez vide pour garder le comportement par défaut.
              </ThemedText>

              <CityPicker label="Ville par défaut" picker={cityPicker} />

              <ChipGroup
                label="Rayon par défaut"
                single
                options={[
                  { value: DEFAULT_RADIUS, label: 'Par défaut' },
                  ...RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` })),
                ]}
                selected={[radius]}
                onToggle={(value) => {
                  setRadius(value);
                  setSaved(false);
                }}
              />

              <ChipGroup
                label="Catégories par défaut (aucune = toutes)"
                options={categories.map((c) => ({ value: c.code, label: c.label }))}
                selected={categoryCodes}
                onToggle={(code) => {
                  setCategoryCodes((current) => toggleCode(current, code));
                  setSaved(false);
                }}
              />

              {saveError && (
                <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
                  Erreur : {saveError}
                </ThemedText>
              )}
              {saved && <ThemedText accessibilityLiveRegion="polite">✓ Paramètres enregistrés.</ThemedText>}

              <Button
                label="Enregistrer"
                loading={saving}
                disabled={cityPicker.blocksSubmit}
                onPress={() => void save()}
              />
            </>
          )}
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
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
});
