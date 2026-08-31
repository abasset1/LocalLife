import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';

/**
 * Onglet "Compte". L'authentification (inscription/connexion, stockage
 * sécurisé du token) sera implémentée en LL-MOB-0004. Pour LL-MOB-0002,
 * écran de navigation uniquement.
 */
export default function CompteScreen() {
  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ThemedText type="title" style={styles.title}>
          Compte
        </ThemedText>
        <ThemedText themeColor="textSecondary">
          L&apos;inscription et la connexion seront implémentées avec
          LL-MOB-0004.
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
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.six,
    gap: Spacing.three,
  },
  title: {
    marginBottom: Spacing.two,
  },
});
