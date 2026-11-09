import { Tabs } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useEffect } from 'react';
import type { ColorValue } from 'react-native';

import { hasOpenOffer, useLaunchFeatures } from '@/features/launch';
import { initMapLayer, shownLayer, useMapLayer } from '@/features/map/layerStore';
import { setNightMode } from '@/hooks/use-night-mode';
import { useTheme } from '@/hooks/use-theme';

const ICONS: Record<'map' | 'list' | 'add' | 'account', SymbolViewProps['name']> = {
  map: { ios: 'map', android: 'map', web: 'map' },
  list: { ios: 'list.bullet.rectangle', android: 'view_list', web: 'view_list' },
  add: { ios: 'plus.circle', android: 'add_circle', web: 'add_circle' },
  account: { ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' },
};

function tabIcon(name: keyof typeof ICONS) {
  return function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <SymbolView name={ICONS[name]} tintColor={color} size={size} />;
  };
}

/**
 * Écrans principaux : Carte, Liste, Contribuer, Compte (LL-MOB-0002). Barre d'onglets du bas rétablie par l'issue #226
 * (navigation permanente de la maquette de l'écran Carte), avec les mêmes entrées principales qu'avant ; le menu
 * rétractable (☰, FRONT-REV-02) reste en haut des écrans pour toutes les autres rubriques. Chaque vue garde son état
 * (position de la carte, défilement de la liste) quand on en change. « Contribuer » n'apparaît que si une offre
 * (Premium ou Pro) est ouverte, comme dans le menu.
 *
 * Les couleurs suivent le thème affiché, thème Night compris : l'univers Night (choisi sur la carte ou dans la liste)
 * fait passer toute l'application en violet nuit, néon et logo du bouc, comme sur le site.
 *
 * Le détail d'une activité n'est volontairement pas ici : il est atteint depuis la carte ou la liste
 * via un écran empilé (voir src/app/activite/[id].tsx et src/app/_layout.tsx).
 */
export default function TabsLayout() {
  const theme = useTheme();
  const features = useLaunchFeatures();
  const layer = shownLayer(useMapLayer(), features);
  useEffect(() => {
    void initMapLayer();
  }, []);
  useEffect(() => {
    setNightMode(layer === 'night');
  }, [layer]);

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textSecondary,
        tabBarStyle: { backgroundColor: theme.backgroundElement, borderTopColor: theme.border },
        tabBarLabelStyle: { fontWeight: '700' },
        // Chaque écran a un fond opaque : une vue ne laisse jamais voir la précédente.
        sceneStyle: { backgroundColor: theme.background },
      }}>
      <Tabs.Screen name="index" options={{ title: 'Carte', tabBarIcon: tabIcon('map') }} />
      <Tabs.Screen name="liste" options={{ title: 'Liste', tabBarIcon: tabIcon('list') }} />
      <Tabs.Screen
        name="contribution"
        options={{ title: 'Contribuer', tabBarIcon: tabIcon('add'), href: hasOpenOffer(features) ? undefined : null }}
      />
      <Tabs.Screen name="compte" options={{ title: 'Compte', tabBarIcon: tabIcon('account') }} />
    </Tabs>
  );
}
