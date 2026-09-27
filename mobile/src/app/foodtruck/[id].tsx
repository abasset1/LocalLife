import { useLocalSearchParams, useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, Linking, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Field } from '@/components/field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { contactAction } from '@/features/foodtruck/contact';
import { useFoodTruck } from '@/features/foodtruck/useFoodTruck';

/**
 * Fiche d'un food truck (LL-MOB-0009), atteinte depuis son marqueur sur la
 * carte. Les informations viennent de l'API (`GET /foodtrucks`) ; le mobile
 * n'ajoute aucune logique propre aux food trucks.
 */
export default function FoodTruckDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { state, retry } = useFoodTruck(id);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['bottom', 'left', 'right']}>
        {state.status === 'loading' && (
          <View style={styles.center} accessibilityLiveRegion="polite">
            <ActivityIndicator size="large" accessibilityLabel="Chargement du food truck" />
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
              <ThemedText type="subtitle" accessibilityRole="header">
                {name}
              </ThemedText>
              <ThemedText themeColor="textSecondary">Food truck</ThemedText>

              <Field label="Cuisine" value={category} />
              {description ? <Field label="Description" value={description} /> : null}
              {contact?.trim() ? <Field label="Contact" value={contact.trim()} /> : null}

              {action && action.kind !== 'text' && (
                <Button
                  label={action.label}
                  variant="secondary"
                  onPress={() => {
                    if (action.kind === 'web') {
                      WebBrowser.openBrowserAsync(action.url).catch(() => undefined);
                    } else {
                      Linking.openURL(action.url).catch(() => undefined);
                    }
                  }}
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
});
