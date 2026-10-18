import { Tabs } from 'expo-router';

import { useTheme } from '@/hooks/use-theme';

/**
 * Écrans principaux : Carte, Liste, Contribuer, Compte (LL-MOB-0002). Depuis FRONT-REV-02, la barre
 * d'onglets est remplacée par un menu rétractable (`MenuButton`, en haut de chaque écran) et par la
 * bascule Carte/Liste : le navigateur d'onglets est conservé, sans sa barre, pour que chaque vue garde
 * son état (position de la carte, défilement de la liste) quand on en change.
 *
 * Le détail d'une activité n'est volontairement pas ici : il est atteint depuis la carte ou la liste
 * via un écran empilé (voir src/app/activite/[id].tsx et src/app/_layout.tsx).
 */
export default function TabsLayout() {
  const theme = useTheme();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { display: 'none' },
        // Chaque écran a un fond opaque : une vue ne laisse jamais voir la précédente.
        sceneStyle: { backgroundColor: theme.background },
      }}>
      <Tabs.Screen name="index" options={{ title: 'Carte' }} />
      <Tabs.Screen name="liste" options={{ title: 'Liste' }} />
      <Tabs.Screen name="contribution" options={{ title: 'Contribuer' }} />
      <Tabs.Screen name="compte" options={{ title: 'Compte' }} />
    </Tabs>
  );
}
