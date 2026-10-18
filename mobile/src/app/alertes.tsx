import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
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
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/auth';
import {
  ChannelSwitch,
  ChipGroup,
  CityPicker,
  DEFAULT_ALERT_RADIUS_KM,
  FOOD_TRUCKS_CHIP,
  RADIUS_OPTIONS_KM,
  alertCriteria,
  toggleCode,
  useCityPicker,
} from '@/features/settings';
import { enablePushOrExplain } from '@/notifications';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}



/**
 * Alertes (partagées avec le site web) : nouvelles activités publiées dans
 * une ou plusieurs catégories, dans un rayon autour d'une ville. Chaque
 * alerte est envoyée par notification sur ce téléphone et/ou par e-mail
 * (récapitulatif quotidien côté backend).
 */
export default function AlertesScreen() {
  const { state } = useAuth();
  const [categories, setCategories] = useState<Category[]>([]);
  const [alerts, setAlerts] = useState<ActivityAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [busyAlertId, setBusyAlertId] = useState<number | null>(null);

  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [includeFoodTrucks, setIncludeFoodTrucks] = useState(false);
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
    if (categoryCodes.length === 0 && !includeFoodTrucks) {
      setCreateError('Choisissez au moins une catégorie, ou les food trucks.');
      return;
    }
    if (!notifyPush && !notifyEmail) {
      setCreateError('Choisissez au moins un moyen d’être prévenu.');
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
      const alert = await createAlert({
        categoryCodes,
        includeFoodTrucks,
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
        const pushProblem = await enablePushOrExplain();
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
        <ThemedText>Connectez-vous depuis la rubrique Compte du menu pour gérer vos alertes.</ThemedText>
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
            Soyez prévenu chaque matin des nouvelles activités et des nouveaux food trucks qui correspondent à
            vos critères.
          </ThemedText>

          {alerts.length === 0 && (
            <Card style={styles.empty}>
              <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                🔔
              </ThemedText>
              <ThemedText style={styles.emptyText}>Aucune alerte pour l’instant.</ThemedText>
            </Card>
          )}
          {alerts.map((alert) => (
            <Card key={alert.id} style={styles.card}>
              <ThemedText type="heading">{alertCriteria(alert, categories)}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                📍 À moins de {alert.radiusKm} km de {alert.city}
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
                variant="danger"
                loading={busyAlertId === alert.id}
                onPress={() => void remove(alert)}
              />
            </Card>
          ))}
          {listError && (
            <Notice tone="danger">Erreur : {listError}</Notice>
          )}

          <Card style={styles.form}>
          <ThemedText type="subtitle" style={styles.subtitle}>
            Nouvelle alerte
          </ThemedText>
          <ChipGroup
            label="Catégories et/ou food trucks"
            options={[
              ...categories.map((c) => ({ value: c.code, label: c.label })),
              { value: FOOD_TRUCKS_CHIP, label: '🚚 Food trucks' },
            ]}
            selected={includeFoodTrucks ? [...categoryCodes, FOOD_TRUCKS_CHIP] : categoryCodes}
            onToggle={(value) => {
              if (value === FOOD_TRUCKS_CHIP) setIncludeFoodTrucks((current) => !current);
              else setCategoryCodes((current) => toggleCode(current, value));
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
            <Notice tone="danger">Erreur : {createError}</Notice>
          )}
          {created && <Notice tone="success">Alerte créée.</Notice>}

          <Button
            label="Créer l'alerte"
            loading={creating}
            disabled={cityPicker.blocksSubmit}
            onPress={() => void create()}
          />
          </Card>
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
  },
  form: {
    gap: Spacing.four,
    padding: Spacing.four - Spacing.one,
  },
  empty: {
    padding: Spacing.four,
    gap: Spacing.two,
  },
  emptyIcon: {
    fontSize: 36,
    lineHeight: 44,
    textAlign: 'center',
  },
  emptyText: {
    textAlign: 'center',
  },
});
