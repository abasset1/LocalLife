import { useLocalSearchParams, useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { isActivityDetail } from '@/api';
import { Button } from '@/components/button';
import { Field } from '@/components/field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { formatDateRange, formatPlace, isSafeWebUrl } from '@/features/activity/format';
import { useActivity } from '@/features/activity/useActivity';

/**
 * Détail d'une activité (LL-MOB-0008), atteint depuis l'aperçu d'un marqueur
 * de la carte. Les informations affichées viennent de `GET /activities/{id}` ;
 * seule la source (absente de cette réponse) est transmise par l'aperçu, qui
 * l'a reçue de la recherche sur la carte.
 */
export default function ActiviteDetailScreen() {
  const { id, source } = useLocalSearchParams<{ id: string; source?: string }>();
  const router = useRouter();
  const { state, retry } = useActivity(id);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['bottom', 'left', 'right']}>
        {state.status === 'loading' && (
          <View style={styles.center} accessibilityLiveRegion="polite">
            <ActivityIndicator size="large" accessibilityLabel="Chargement de l'activité" />
          </View>
        )}

        {state.status === 'error' && (
          <View style={styles.center} accessibilityRole="alert">
            <ThemedText style={styles.centerText}>{state.message}</ThemedText>
            <Button label="Réessayer" onPress={retry} />
            <Button label="Retour à la carte" variant="secondary" onPress={() => router.back()} />
          </View>
        )}

        {state.status === 'unavailable' && (
          <View style={styles.center} accessibilityRole="alert">
            <ThemedText type="subtitle" style={styles.centerText}>
              Activité indisponible
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.centerText}>
              Cette activité n&apos;existe pas ou n&apos;est plus disponible.
            </ThemedText>
            <Button label="Retour à la carte" onPress={() => router.back()} />
          </View>
        )}

        {state.status === 'success' && (() => {
          const activity = state.activity;
          const detail = isActivityDetail(activity) ? activity : null;
          const description = detail ? (detail.longDescription ?? detail.description) : null;
          const date = isActivityDetail(activity)
            ? formatDateRange(activity.startDate, activity.endDate)
            : formatDateRange(activity.date);
          const place = formatPlace(
            activity.address,
            detail ? detail.postalCode : null,
            activity.city,
          );
          const url = detail?.url;

          return (
            <ScrollView contentContainerStyle={styles.content}>
              <ThemedText type="subtitle" accessibilityRole="header">
                {activity.title}
              </ThemedText>
              <ThemedText themeColor="textSecondary">{activity.category}</ThemedText>

              {date && <Field label="Date" value={date} />}
              {place && <Field label="Lieu" value={place} />}
              {source ? <Field label="Source" value={source} /> : null}

              {description ? (
                <View style={styles.field}>
                  <ThemedText type="smallBold" themeColor="textSecondary">
                    Description
                  </ThemedText>
                  <ThemedText>{description}</ThemedText>
                </View>
              ) : null}

              {isSafeWebUrl(url) && (
                <Button
                  label="Ouvrir le site de l'activité"
                  variant="secondary"
                  onPress={() => WebBrowser.openBrowserAsync(url).catch(() => undefined)}
                />
              )}
              <Button label="Retour à la carte" onPress={() => router.back()} />
            </ScrollView>
          );
        })()}
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  content: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    gap: Spacing.three,
  },
  centerText: {
    textAlign: 'center',
  },
  field: {
    gap: Spacing.one,
  },
});
