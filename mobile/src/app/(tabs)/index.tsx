import { Link } from 'expo-router';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';

/**
 * Onglet "Découvrir". Portera la carte interactive et la liste des
 * activités (LL-MOB-0005 carte, LL-MOB-0006 géolocalisation, LL-MOB-0007
 * filtres). Pour LL-MOB-0002, c'est un écran d'accueil de la navigation.
 */
export default function DecouvrirScreen() {
  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ThemedText type="title" style={styles.title}>
          Découvrir
        </ThemedText>
        <ThemedText themeColor="textSecondary">
          La carte interactive, la géolocalisation et les filtres arriveront
          avec les tickets suivants du sprint mobile.
        </ThemedText>

        {/*
          Démonstration temporaire de la navigation vers le détail d'une
          activité (objet de LL-MOB-0002). Ce lien sera remplacé par la
          navigation réelle depuis la carte/liste en LL-MOB-0008.
        */}
        <Link
          href={{ pathname: '/activite/[id]', params: { id: 'demo' } }}
          style={styles.demoLink}>
          <ThemedText type="linkPrimary">
            Exemple : voir le détail d&apos;une activité →
          </ThemedText>
        </Link>
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
  demoLink: {
    marginTop: Spacing.four,
  },
});
