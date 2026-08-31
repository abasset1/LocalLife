import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';

SplashScreen.preventAutoHideAsync();

/**
 * Layout racine (LL-MOB-0002) : le groupe `(tabs)` porte la navigation par
 * onglets (Découvrir / Contribution / Compte), et le détail d'une activité
 * est un écran empilé au-dessus, avec un header natif (bouton retour +
 * geste retour Android géré nativement par le stack navigator, aucun code
 * manuel requis).
 */
export default function RootLayout() {
  const colorScheme = useColorScheme();
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <AnimatedSplashOverlay />
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen
          name="activite/[id]"
          options={{ title: "Détail de l'activité" }}
        />
      </Stack>
    </ThemeProvider>
  );
}
