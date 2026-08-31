import { useLocalSearchParams } from 'expo-router';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';

/**
 * Détail d'une activité, atteint en écran empilé depuis l'onglet
 * "Découvrir" (LL-MOB-0002). Le contenu réel (description, horaires,
 * lieu, food trucks associés, appel à l'API…) sera implémenté en
 * LL-MOB-0008 ; ici on ne pose que la route et la navigation.
 */
export default function ActiviteDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['bottom', 'left', 'right']}>
        <ThemedText type="subtitle">Détail de l&apos;activité</ThemedText>
        <ThemedText themeColor="textSecondary">Identifiant : {id}</ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.note}>
          Le contenu réel de cet écran (description, horaires, lieu, appel à
          l&apos;API…) sera implémenté en LL-MOB-0008.
        </ThemedText>
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
    padding: Spacing.four,
    gap: Spacing.three,
  },
  note: {
    marginTop: Spacing.three,
  },
});
