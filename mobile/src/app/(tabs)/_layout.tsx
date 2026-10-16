import { Tabs } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { StyleSheet, type ColorValue } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useTheme } from '@/hooks/use-theme';

/**
 * Navigation par onglets : Découvrir (carte), Liste, Contribuer, Compte
 * (LL-MOB-0002). Le détail d'une activité n'est volontairement pas un
 * onglet : il est atteint depuis la carte ou la liste via un écran empilé
 * (voir src/app/activite/[id].tsx et src/app/_layout.tsx).
 *
 * Utilise le `Tabs` standard de expo-router plutôt que `NativeTabs`
 * (API instable) : rendu identique sur toutes les plateformes, pas de
 * double implémentation à maintenir.
 */
/** Icône d'onglet : SF Symbol sur iOS, Material Symbol sur Android et le web ; pleine si l'onglet est actif. */
function tabIcon(name: SymbolViewProps['name'], activeName: SymbolViewProps['name']) {
  function TabIcon({ color, focused }: { color: ColorValue; focused: boolean }) {
    return <SymbolView name={focused ? activeName : name} tintColor={color} size={ICON_SIZE} />;
  }
  return TabIcon;
}

const ICON_SIZE = 24;
/** Hauteur de la barre hors zone de sécurité : icône + libellé sur deux lignes distinctes. */
const TAB_BAR_HEIGHT = 66;

const ICONS = {
  decouvrir: tabIcon({ ios: 'map', android: 'map', web: 'map' }, { ios: 'map.fill', android: 'map', web: 'map' }),
  liste: tabIcon(
    { ios: 'list.bullet.rectangle', android: 'view_list', web: 'view_list' },
    { ios: 'list.bullet.rectangle.fill', android: 'view_list', web: 'view_list' },
  ),
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
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        // Libellé toujours sous l'icône (sinon « à côté » sur les grands écrans, où les onglets se chevauchent).
        tabBarLabelPosition: 'below-icon',
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textSecondary,
        tabBarLabelStyle: styles.label,
        tabBarItemStyle: styles.item,
        // Hauteur explicite, zone de sécurité (barre de gestes Android, indicateur iOS) comprise.
        tabBarStyle: {
          height: TAB_BAR_HEIGHT + insets.bottom,
          paddingBottom: insets.bottom,
          backgroundColor: theme.backgroundElement,
          borderTopColor: theme.border,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
        // Chaque écran a un fond opaque : un onglet ne laisse jamais voir le précédent.
        sceneStyle: { backgroundColor: theme.background },
      }}>
      <Tabs.Screen name="index" options={{ title: 'Découvrir', tabBarIcon: ICONS.decouvrir }} />
      <Tabs.Screen name="liste" options={{ title: 'Liste', tabBarIcon: ICONS.liste }} />
      <Tabs.Screen name="contribution" options={{ title: 'Contribuer', tabBarIcon: ICONS.contribution }} />
      <Tabs.Screen name="compte" options={{ title: 'Compte', tabBarIcon: ICONS.compte }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  item: {
    paddingVertical: 6,
  },
  label: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '700',
    marginTop: 2,
  },
});
