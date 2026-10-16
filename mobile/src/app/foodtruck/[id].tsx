import { useLocalSearchParams, useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, Linking, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Field } from '@/components/field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { contactAction } from '@/features/foodtruck/contact';
import { useFoodTruck } from '@/features/foodtruck/useFoodTruck';
import { useTheme } from '@/hooks/use-theme';

/**
 * Fiche d'un food truck (LL-MOB-0009), atteinte depuis son marqueur sur la
 * carte. Les informations viennent de l'API (`GET /foodtrucks`) ; le mobile
 * n'ajoute aucune logique propre aux food trucks.
 */
export default function FoodTruckDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();
  const { state, retry } = useFoodTruck(id);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['bottom', 'left', 'right']}>
        {state.status === 'loading' && (
          <View style={styles.center} accessibilityLiveRegion="polite">
            <ActivityIndicator size="large" color={theme.primary} accessibilityLabel="Chargement du food truck" />
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
              Food truck indisponible
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.centerText}>
              Ce food truck n&apos;existe pas ou n&apos;est plus disponible.
            </ThemedText>
            <Button label="Retour à la carte" onPress={() => router.back()} />
          </View>
        )}

        {state.status === 'success' && (() => {
          const { name, category, description, contact } = state.foodTruck;
          const action = contactAction(contact);

          return (
            <ScrollView contentContainerStyle={styles.content}>
              <View style={[styles.hero, { backgroundColor: theme.accentSoft }]}>
                <ThemedText style={styles.heroIcon} importantForAccessibility="no">
                  🚚
                </ThemedText>
                <ThemedText type="eyebrow">Food truck</ThemedText>
                <ThemedText type="subtitle" accessibilityRole="header">
                  {name}
                </ThemedText>
              </View>

              <Card style={styles.fields}>
                <Field label="Cuisine" value={category} />
                {description ? <Field label="Description" value={description} /> : null}
                {contact?.trim() ? <Field label="Contact" value={contact.trim()} /> : null}
              </Card>

              {action && action.kind !== 'text' && (
                <Button
                  label={action.label}
                  onPress={() => {
                    if (action.kind === 'web') {
                      WebBrowser.openBrowserAsync(action.url).catch(() => undefined);
                    } else {
                      Linking.openURL(action.url).catch(() => undefined);
                    }
                  }}
                />
              )}
              <Button label="Retour à la carte" variant="ghost" onPress={() => router.back()} />
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
  heroIcon: {
    fontSize: 36,
    lineHeight: 44,
  },
  fields: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
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
