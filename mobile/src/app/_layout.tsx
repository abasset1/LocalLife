import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useColorScheme } from 'react-native';

import { AuthProvider } from '@/auth';
import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { OnboardingGate } from '@/features/onboarding';
import { useNotificationRouting } from '@/notifications';

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
  // Toucher une notification d'alerte ouvre la fiche de l'activité (ou la carte).
  useNotificationRouting();
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <AnimatedSplashOverlay />
      <AuthProvider>
        <Stack>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen
            name="activite/[id]"
            options={{ title: "Détail de l'activité" }}
          />
          <Stack.Screen name="foodtruck/[id]" options={{ title: 'Food truck' }} />
          <Stack.Screen name="parametres" options={{ title: 'Paramètres de recherche' }} />
          <Stack.Screen name="alertes" options={{ title: 'Mes alertes' }} />
          <Stack.Screen name="contributions/index" options={{ title: 'Mes contributions' }} />
          <Stack.Screen name="contributions/[id]" options={{ title: "Modifier l'activité" }} />
          {/* Accueil de première connexion : plein écran, sans geste ni bouton de retour. */}
          <Stack.Screen
            name="bienvenue"
            options={{ headerShown: false, gestureEnabled: false, presentation: 'fullScreenModal' }}
          />
        </Stack>
        <OnboardingGate />
      </AuthProvider>
    </ThemeProvider>
  );
}
