import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useRef } from 'react';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, Linking, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { isActivityDetail } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Field } from '@/components/field';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateRange, formatPlace, isSafeWebUrl } from '@/features/activity/format';
import { buildDirectionsUrl, withApproximateMention } from '@/features/activity/location';
import { useActivity } from '@/features/activity/useActivity';
import { useAuth } from '@/auth';
import { ContributorActions, STATUS_LABELS } from '@/features/contribution';

/**
 * Détail d'une activité (LL-MOB-0008), atteint depuis l'aperçu d'un marqueur
 * de la carte. Les informations affichées viennent de `GET /activities/{id}` ;
 * seule la source (absente de cette réponse) est transmise par l'aperçu, qui
 * l'a reçue de la recherche sur la carte.
 */
export default function ActiviteDetailScreen() {
  const { id, source } = useLocalSearchParams<{ id: string; source?: string }>();
  const router = useRouter();
  const theme = useTheme();
  const { state: auth } = useAuth();
  const { state, retry } = useActivity(id, auth.status === 'authenticated' ? auth.user : null);

  // Retour sur la fiche après « Modifier » : rechargée pour refléter la modification.
  const focusedOnce = useRef(false);
  useFocusEffect(
    useCallback(() => {
      if (focusedOnce.current) retry();
      focusedOnce.current = true;
    }, [retry]),
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['bottom', 'left', 'right']}>
        {state.status === 'loading' && (
          <View style={styles.center} accessibilityLiveRegion="polite">
            <ActivityIndicator size="large" color={theme.primary} accessibilityLabel="Chargement de l'activité" />
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
          // Position approximative (une zone) : mention, et itinéraire vers l'adresse ou la commune.
          const place = withApproximateMention(
            formatPlace(activity.address, detail ? detail.postalCode : null, activity.city),
            activity,
          );
          const directions = buildDirectionsUrl(activity);
          const url = detail?.url;
          const publishedOn = detail ? formatDateRange(detail.publishedAt) : null;

          return (
            <ScrollView contentContainerStyle={styles.content}>
              <View style={[styles.hero, { backgroundColor: theme.sand }]}>
                <View style={[styles.badge, { backgroundColor: theme.backgroundElement }]}>
                  <ThemedText type="eyebrow" themeColor="primary">
                    {activity.category}
                  </ThemedText>
                </View>
                {date ? (
                  <ThemedText type="eyebrow" style={styles.heroDate}>
                    {date}
                  </ThemedText>
                ) : null}
                <ThemedText type="subtitle" accessibilityRole="header">
                  {activity.title}
                </ThemedText>
              </View>
              {detail && detail.status !== 'PUBLISHED' && (
                <Notice tone="warning">
                  {STATUS_LABELS[detail.status] ?? detail.status} : visible seulement par vous (et les
                  modérateurs) tant qu’elle n’est pas publiée.
                </Notice>
              )}

              <Card style={styles.fields}>
                {date && <Field label="Date" value={date} />}
                {place && <Field label="Lieu" value={place} />}
                {source ? <Field label="Source" value={source} /> : null}
                {publishedOn && <Field label="Publiée le" value={publishedOn} />}
              </Card>

              {description ? (
                <Card style={styles.fields}>
                  <ThemedText type="heading">Description</ThemedText>
                  <ThemedText style={styles.description}>{description}</ThemedText>
                </Card>
              ) : null}

              <View style={styles.actions}>
                {directions && (
                  <Button
                    label="Itinéraire"
                    icon={{ ios: 'arrow.triangle.turn.up.right.diamond', android: 'directions', web: 'directions' }}
                    onPress={() => Linking.openURL(directions).catch(() => undefined)}
                  />
                )}
                {isSafeWebUrl(url) && (
                  <Button
                    label="Ouvrir le site de l'activité"
                    variant="secondary"
                    icon={{ ios: 'safari', android: 'language', web: 'language' }}
                    onPress={() => WebBrowser.openBrowserAsync(url).catch(() => undefined)}
                  />
                )}
                <ContributorActions
                  activityId={activity.id}
                  title={activity.title}
                  createdBy={activity.createdBy}
                  // Fiche ouverte depuis une notification : pas d'écran précédent, retour à la carte.
                  onDeleted={() => (router.canGoBack() ? router.back() : router.replace('/'))}
                />
                <Button
                  label="Retour"
                  variant="ghost"
                  onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
                />
              </View>
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
    padding: Spacing.three,
    paddingBottom: Spacing.five,
    gap: Spacing.three,
  },
  hero: {
    borderRadius: Radius.xl,
    padding: Spacing.four,
    gap: Spacing.two,
  },
  badge: {
    alignSelf: 'flex-start',
    paddingHorizontal: Spacing.two + Spacing.one,
    paddingVertical: Spacing.one + Spacing.half,
    borderRadius: Radius.pill,
    marginBottom: Spacing.two,
  },
  heroDate: {
    letterSpacing: 0.7,
  },
  fields: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
  description: {
    lineHeight: 26,
  },
  actions: {
    gap: Spacing.two,
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
});
