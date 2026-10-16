import { Tabs } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { StyleSheet, type ColorValue } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

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
/** Icône d'onglet : SF Symbol sur iOS, Material Symbol sur Android et le web ; pleine si l'onglet est actif. */
function tabIcon(name: SymbolViewProps['name'], activeName: SymbolViewProps['name']) {
  function TabIcon({ color, size, focused }: { color: ColorValue; size: number; focused: boolean }) {
    return <SymbolView name={focused ? activeName : name} tintColor={color} size={size} />;
  }
  return TabIcon;
}

const ICONS = {
  decouvrir: tabIcon({ ios: 'map', android: 'map', web: 'map' }, { ios: 'map.fill', android: 'map', web: 'map' }),
  contribution: tabIcon(
    { ios: 'plus.circle', android: 'add_circle', web: 'add_circle' },
    { ios: 'plus.circle.fill', android: 'add_circle', web: 'add_circle' },
  ),
  compte: tabIcon(
    { ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' },
    { ios: 'person.crop.circle.fill', android: 'account_circle', web: 'account_circle' },
  ),
};

export default function TabsLayout() {
  const theme = useTheme();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textSecondary,
        tabBarLabelStyle: styles.label,
        tabBarStyle: {
          backgroundColor: theme.backgroundElement,
          borderTopColor: theme.border,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
      }}>
      <Tabs.Screen name="index" options={{ title: 'Découvrir', tabBarIcon: ICONS.decouvrir }} />
      <Tabs.Screen name="contribution" options={{ title: 'Contribuer', tabBarIcon: ICONS.contribution }} />
      <Tabs.Screen name="compte" options={{ title: 'Compte', tabBarIcon: ICONS.compte }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  label: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
  },
});
