import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme } from 'react-native';

import { AuthProvider } from '@/auth';
import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { Colors } from '@/constants/theme';
import { FavoritesProvider } from '@/features/favorites';
import { OnboardingGate } from '@/features/onboarding';
import { useNotificationRouting } from '@/notifications';

SplashScreen.preventAutoHideAsync();

/** Thèmes de navigation aux couleurs de LocalLife (fond papier, vert forêt, corail). */
function navigationTheme(dark: boolean) {
  const base = dark ? DarkTheme : DefaultTheme;
  const colors = Colors[dark ? 'dark' : 'light'];
  return {
    ...base,
    colors: {
      ...base.colors,
      primary: colors.primary,
      background: colors.background,
      card: colors.backgroundElement,
      text: colors.text,
      border: colors.border,
      notification: colors.accent,
    },
  };
}

/**
 * Layout racine (LL-MOB-0002) : le groupe `(tabs)` porte les écrans
 * principaux (Carte / Liste / Contribuer / Compte, atteints par le menu
 * rétractable depuis FRONT-REV-02), et le détail d'une activité
 * est un écran empilé au-dessus, avec un header natif (bouton retour +
 * geste retour Android géré nativement par le stack navigator, aucun code
 * manuel requis).
 */
export default function RootLayout() {
  const colorScheme = useColorScheme();
  const dark = colorScheme === 'dark';
  const colors = Colors[dark ? 'dark' : 'light'];
  // Toucher une notification d'alerte ouvre la fiche de l'activité (ou la carte).
  useNotificationRouting();
  return (
    <ThemeProvider value={navigationTheme(dark)}>
      <StatusBar style={dark ? 'light' : 'dark'} />
      <AnimatedSplashOverlay />
      <AuthProvider>
        <FavoritesProvider>
          <Stack
            screenOptions={{
              headerStyle: { backgroundColor: colors.background },
              headerTintColor: colors.primary,
              headerTitleStyle: { color: colors.text, fontWeight: '700' },
              headerShadowVisible: false,
              headerBackButtonDisplayMode: 'minimal',
              contentStyle: { backgroundColor: colors.background },
            }}>
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
            <Stack.Screen
              name="activite/[id]"
              options={{ title: 'Activité' }}
            />
            <Stack.Screen name="foodtruck/[id]" options={{ title: 'Food truck' }} />
            <Stack.Screen name="parametres" options={{ title: 'Paramètres de recherche' }} />
            <Stack.Screen name="alertes" options={{ title: 'Mes alertes' }} />
            <Stack.Screen name="favoris" options={{ title: 'Mes favoris' }} />
            <Stack.Screen name="mot-de-passe-oublie" options={{ title: 'Mot de passe oublié' }} />
            <Stack.Screen name="contributions/index" options={{ title: 'Mes contributions' }} />
            <Stack.Screen name="contributions/[id]" options={{ title: "Modifier l'activité" }} />
            <Stack.Screen name="abonnement" options={{ title: 'Mon abonnement' }} />
            <Stack.Screen name="securite" options={{ title: 'Mot de passe et compte' }} />
            <Stack.Screen name="pro/index" options={{ title: 'Espace professionnel' }} />
            <Stack.Screen name="pro/revendiquer" options={{ title: 'Gérer un lieu existant' }} />
            <Stack.Screen name="pro/nouveau-lieu" options={{ title: 'Créer un lieu' }} />
            <Stack.Screen name="pro/foodtrucks/index" options={{ title: 'Mes food trucks' }} />
            <Stack.Screen name="pro/foodtrucks/[id]" options={{ title: 'Mon food truck' }} />
            {/* Accueil de première connexion : plein écran, sans geste ni bouton de retour. */}
            <Stack.Screen
              name="bienvenue"
              options={{ headerShown: false, gestureEnabled: false, presentation: 'fullScreenModal' }}
            />
          </Stack>
          <OnboardingGate />
        </FavoritesProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
