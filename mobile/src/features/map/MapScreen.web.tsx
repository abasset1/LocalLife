import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { MenuButton } from '@/features/navigation/AppMenu';

/** `react-native-maps` ne fonctionne pas sur le web : la carte est réservée à Android et iOS. */
export default function MapScreen() {
  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <MenuButton />
        <ThemedText type="title">Découvrir</ThemedText>
        <ThemedText themeColor="textSecondary">
          La carte est disponible uniquement sur Android et iOS. Utilisez la vue Liste (menu) ou le site web
          pour explorer les activités depuis un navigateur.
        </ThemedText>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1, padding: Spacing.four, gap: Spacing.three },
});
