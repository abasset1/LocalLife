import { Tabs } from 'expo-router';
import { useColorScheme } from 'react-native';

import { Colors } from '@/constants/theme';

/**
 * Navigation par onglets du sprint mobile : Découvrir, Contribution,
 * Compte (LL-MOB-0002). Le détail d'une activité n'est volontairement
 * pas un onglet : il est atteint depuis "Découvrir" via un écran empilé
 * (voir src/app/activite/[id].tsx et src/app/_layout.tsx).
 *
 * Utilise le `Tabs` standard de expo-router plutôt que `NativeTabs`
 * (API instable) : rendu identique sur toutes les plateformes, pas de
 * double implémentation à maintenir.
 */
export default function TabsLayout() {
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'dark' ? 'dark' : 'light'];

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.text,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarStyle: { backgroundColor: colors.background },
      }}>
      <Tabs.Screen name="index" options={{ title: 'Découvrir' }} />
      <Tabs.Screen name="contribution" options={{ title: 'Contribution' }} />
      <Tabs.Screen name="compte" options={{ title: 'Compte' }} />
    </Tabs>
  );
}
