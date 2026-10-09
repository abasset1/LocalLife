import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  View,
} from 'react-native';

import {
  ApiError,
  HttpError,
  createAlert,
  deleteAlert,
  getPreferences,
  listAlerts,
  listCategories,
  updateAlertChannels,
} from '@/api';
import type { ActivityAlert, Category } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/auth';
import {
  ChipGroup,
  CityPicker,
  DEFAULT_ALERT_RADIUS_KM,
  RADIUS_OPTIONS_KM,
  categoryLabels,
  toggleCode,
  useCityPicker,
} from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';
import { enablePushNotifications } from '@/notifications';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Active les notifications sur cet appareil. Retourne un message d'erreur
 * affichable, ou `null` si l'appareil est bien enregistré.
 */
async function ensurePushEnabled(): Promise<string | null> {
  const result = await enablePushNotifications();
  if (result.status === 'registered') return null;
  if (result.status === 'denied') {
    return 'Les notifications sont refusées : autorisez-les dans les réglages du téléphone.';
  }
  return result.reason;
}

/** Ligne « interrupteur + libellé », zone tactile ≥ 48 px. */
function ChannelSwitch({
  label,
  value,
  onValueChange,
  disabled,
}: {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <View style={styles.switchRow}>
      <ThemedText>{label}</ThemedText>
      <Switch accessibilityLabel={label} value={value} onValueChange={onValueChange} disabled={disabled} />
    </View>
  );
}

/**
 * Alertes (partagées avec le site web) : nouvelles activités publiées dans
 * une ou plusieurs catégories, dans un rayon autour d'une ville. Chaque
 * alerte est envoyée par notification sur ce téléphone et/ou par e-mail
 * (récapitulatif quotidien côté backend).
 */
export default function AlertesScreen() {
  const theme = useTheme();
  const { state } = useAuth();
  const [categories, setCategories] = useState<Category[]>([]);
  const [alerts, setAlerts] = useState<ActivityAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [busyAlertId, setBusyAlertId] = useState<number | null>(null);

  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [radius, setRadius] = useState(String(DEFAULT_ALERT_RADIUS_KM));
  const [notifyPush, setNotifyPush] = useState(true);
  const [notifyEmail, setNotifyEmail] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [created, setCreated] = useState(false);
  const onEdit = useCallback(() => setCreated(false), []);
  const cityPicker = useCityPicker(onEdit);
  const { setSavedCity } = cityPicker;

  const authenticated = state.status === 'authenticated';
  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    const signal = controller.signal;
    Promise.all([listCategories({ signal }), listAlerts({ signal }), getPreferences({ signal })])
      .then(([allCategories, existing, preferences]) => {
        setCategories(
          allCategories.filter((c) => c.active).sort((a, b) => a.label.localeCompare(b.label, 'fr')),
        );
        setAlerts(existing);
        // Nouvelle alerte préremplie avec les paramètres de recherche.
        setCategoryCodes(preferences.defaultCategoryCodes);
        setSavedCity(preferences.defaultCity);
        if (preferences.defaultRadiusKm !== null) setRadius(String(preferences.defaultRadiusKm));
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (signal.aborted) return;
        setListError(messageFor(e));
        setLoading(false);
      });
    return () => controller.abort();
  }, [authenticated, setSavedCity]);

  async function create() {
    setCreateError(null);
    setCreated(false);
    if (categoryCodes.length === 0) {
      setCreateError('Choisissez au moins une catégorie.');
      return;
    }
    if (!notifyPush && !notifyEmail) {
      setCreateError('Choisissez au moins un moyen d’être prévenu.');
      return;
    }
    setCreating(true);
    try {
      if (notifyPush) {
        const pushProblem = await ensurePushEnabled();
        if (pushProblem) {
          setCreateError(pushProblem);
          return;
        }
      }
      const alert = await createAlert({
        categoryCodes,
        city: cityPicker.city.trim() || null,
        latitude: cityPicker.position?.latitude ?? null,
        longitude: cityPicker.position?.longitude ?? null,
        radiusKm: Number(radius),
        notifyEmail,
        notifyPush,
      });
      setAlerts((current) => [...current, alert]);
      setCreated(true);
    } catch (e) {
      setCreateError(messageFor(e));
    } finally {
      setCreating(false);
    }
  }

  async function changeChannels(alert: ActivityAlert, channels: { notifyEmail: boolean; notifyPush: boolean }) {
    setListError(null);
    if (!channels.notifyEmail && !channels.notifyPush) {
      setListError('Gardez au moins un moyen d’être prévenu, ou supprimez l’alerte.');
      return;
    }
    setBusyAlertId(alert.id);
    try {
      if (channels.notifyPush && !alert.notifyPush) {
        const pushProblem = await ensurePushEnabled();
        if (pushProblem) {
          setListError(pushProblem);
          return;
        }
      }
      const updated = await updateAlertChannels(alert.id, channels);
      setAlerts((current) => current.map((a) => (a.id === updated.id ? updated : a)));
    } catch (e) {
      setListError(messageFor(e));
    } finally {
      setBusyAlertId(null);
    }
  }

  async function remove(alert: ActivityAlert) {
    setListError(null);
    setBusyAlertId(alert.id);
    try {
      await deleteAlert(alert.id);
      setAlerts((current) => current.filter((a) => a.id !== alert.id));
    } catch (e) {
      if (e instanceof HttpError && e.isNotFound) {
        setAlerts((current) => current.filter((a) => a.id !== alert.id));
      } else {
        setListError(messageFor(e));
      }
    } finally {
      setBusyAlertId(null);
    }
  }

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText>Connectez-vous depuis l’onglet Compte pour gérer vos alertes.</ThemedText>
      </ThemedView>
    );
  }

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement des alertes" />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <ThemedText themeColor="textSecondary">
            Soyez prévenu chaque matin des nouvelles activités qui correspondent à vos critères.
          </ThemedText>

          {alerts.length === 0 && <ThemedText>Aucune alerte pour l’instant.</ThemedText>}
          {alerts.map((alert) => (
            <View key={alert.id} style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">{categoryLabels(alert.categoryCodes, categories)}</ThemedText>
              <ThemedText type="small">
                À moins de {alert.radiusKm} km de {alert.city}
              </ThemedText>
              <ChannelSwitch
                label="Notification"
                value={alert.notifyPush}
                disabled={busyAlertId === alert.id}
                onValueChange={(value) =>
                  void changeChannels(alert, { notifyEmail: alert.notifyEmail, notifyPush: value })
                }
              />
              <ChannelSwitch
                label="E-mail"
                value={alert.notifyEmail}
                disabled={busyAlertId === alert.id}
                onValueChange={(value) =>
                  void changeChannels(alert, { notifyEmail: value, notifyPush: alert.notifyPush })
                }
              />
              <Button
                label="Supprimer"
                variant="secondary"
                loading={busyAlertId === alert.id}
                onPress={() => void remove(alert)}
              />
            </View>
          ))}
          {listError && (
            <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
              Erreur : {listError}
            </ThemedText>
          )}

          <ThemedText type="subtitle" style={styles.subtitle}>
            Nouvelle alerte
          </ThemedText>
          <ChipGroup
            label="Catégories (une ou plusieurs)"
            options={categories.map((c) => ({ value: c.code, label: c.label }))}
            selected={categoryCodes}
            onToggle={(code) => {
              setCategoryCodes((current) => toggleCode(current, code));
              setCreated(false);
            }}
          />
          <CityPicker label="Ville" picker={cityPicker} />
          <ChipGroup
            label="Rayon"
            single
            options={RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` }))}
            selected={[radius]}
            onToggle={(value) => {
              setRadius(value);
              setCreated(false);
            }}
          />
          <ChannelSwitch label="Notification sur ce téléphone" value={notifyPush} onValueChange={setNotifyPush} />
          <ChannelSwitch label="E-mail" value={notifyEmail} onValueChange={setNotifyEmail} />

          {createError && (
            <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
              Erreur : {createError}
            </ThemedText>
          )}
          {created && <ThemedText accessibilityLiveRegion="polite">✓ Alerte créée.</ThemedText>}

          <Button
            label="Créer l'alerte"
            loading={creating}
            disabled={cityPicker.blocksSubmit}
            onPress={() => void create()}
          />
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
  subtitle: {
    fontSize: 22,
    lineHeight: 28,
  },
  card: {
    gap: Spacing.two,
    padding: Spacing.three,
    borderRadius: 12,
  },
  switchRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
});
