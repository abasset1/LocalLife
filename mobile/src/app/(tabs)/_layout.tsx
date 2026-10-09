import { Tabs } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useColorScheme, type ColorValue } from 'react-native';

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
/** Icône d'onglet : SF Symbol sur iOS, Material Symbol sur Android et le web. */
function tabIcon(name: SymbolViewProps['name']) {
  function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <SymbolView name={name} tintColor={color} size={size} />;
  }
  return TabIcon;
}

const ICONS = {
  decouvrir: tabIcon({ ios: 'map', android: 'map', web: 'map' }),
  contribution: tabIcon({ ios: 'plus.circle', android: 'add_circle', web: 'add_circle' }),
  compte: tabIcon({ ios: 'person', android: 'person', web: 'person' }),
};

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
      <Tabs.Screen name="index" options={{ title: 'Découvrir', tabBarIcon: ICONS.decouvrir }} />
      <Tabs.Screen name="contribution" options={{ title: 'Contribution', tabBarIcon: ICONS.contribution }} />
      <Tabs.Screen name="compte" options={{ title: 'Compte', tabBarIcon: ICONS.compte }} />
    </Tabs>
  );
}
