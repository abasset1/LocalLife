import { DarkTheme, DefaultTheme, Stack, ThemeProvider, useSegments } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';

import { AuthGate, AuthProvider } from '@/auth';
import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { ScreenError } from '@/components/screen-error';
import { Colors } from '@/constants/theme';
import { useThemeName, type ThemeName } from '@/hooks/use-theme';
import { FavoritesProvider } from '@/features/favorites';
import { FollowsProvider } from '@/features/follow';
import { LaunchFeaturesProvider } from '@/features/launch';
import { OnboardingGate } from '@/features/onboarding';
import { useNotificationRouting } from '@/notifications';

SplashScreen.preventAutoHideAsync();

/** Une erreur d'affichage montre un message avec « Réessayer » au lieu de fermer l'application. */
export const ErrorBoundary = ScreenError;

/** Thèmes de navigation aux couleurs de Neiri (fond clair, bleu nuit, rose ; violet et néon pour « Night »). */
function navigationTheme(themeName: ThemeName) {
  const base = themeName === 'light' ? DefaultTheme : DarkTheme;
  const colors = Colors[themeName];
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
 * Couleur des icônes de la barre d'état : claires sous le bandeau bleu nuit des écrans empilés ; sur la carte,
 * les onglets et l'accueil (sans bandeau), selon le thème.
 */
function statusBarStyle(dark: boolean, firstSegment: string | undefined): 'light' | 'dark' {
  if (dark) return 'light';
  const withoutHeader = !firstSegment || firstSegment === '(tabs)' || firstSegment === 'bienvenue';
  return withoutHeader ? 'dark' : 'light';
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
  // Clair ou sombre selon le système, « Night » tant que la carte Night est choisie (comme le site).
  const themeName = useThemeName();
  const dark = themeName !== 'light';
  const colors = Colors[themeName];
  const segments = useSegments();
  // Toucher une notification d'alerte ouvre la fiche de l'activité (ou la carte).
  useNotificationRouting();
  return (
    <ThemeProvider value={navigationTheme(themeName)}>
      {/* Icônes claires sous le bandeau bleu nuit des écrans empilés ; selon le thème sur la carte et l'accueil. */}
      <StatusBar style={statusBarStyle(dark, segments[0])} />
      <AnimatedSplashOverlay />
      <AuthProvider>
        {/* Fonctions ouvertes au lancement (lieux, food trucks, Premium, Pro), relues sur le serveur. */}
        <LaunchFeaturesProvider>
        <FavoritesProvider>
          <FollowsProvider>
          <Stack
            screenOptions={{
              // Bandeau bleu nuit et texte blanc, comme les bandeaux du site.
              headerStyle: { backgroundColor: colors.chrome },
              headerTintColor: colors.onChrome,
              headerTitleStyle: { color: colors.onChrome, fontWeight: '700' },
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
            <Stack.Screen name="suivis" options={{ title: 'Mes suivis' }} />
            <Stack.Screen name="mot-de-passe-oublie" options={{ title: 'Mot de passe oublié' }} />
            <Stack.Screen name="contributions/index" options={{ title: 'Mes contributions' }} />
            <Stack.Screen name="contributions/[id]" options={{ title: "Modifier l'activité" }} />
            <Stack.Screen name="abonnement" options={{ title: 'Mon abonnement' }} />
            <Stack.Screen name="securite" options={{ title: 'Mot de passe et compte' }} />
            <Stack.Screen name="guide" options={{ title: "Guide de l'application" }} />
            <Stack.Screen name="nouveautes" options={{ title: 'Nouveautés' }} />
            <Stack.Screen name="retours" options={{ title: 'Une idée, un problème ?' }} />
            <Stack.Screen name="pro/index" options={{ title: 'Espace professionnel' }} />
            <Stack.Screen name="pro/revendiquer" options={{ title: 'Gérer un lieu existant' }} />
            <Stack.Screen name="pro/nouveau-lieu" options={{ title: 'Créer un lieu' }} />
            <Stack.Screen name="pro/lieu/[id]" options={{ title: 'Mon lieu' }} />
            <Stack.Screen name="pro/foodtrucks/index" options={{ title: 'Mes food trucks' }} />
            <Stack.Screen name="pro/foodtrucks/[id]" options={{ title: 'Mon food truck' }} />
            {/* Accueil de première connexion : plein écran, sans geste ni bouton de retour. */}
            <Stack.Screen
              name="bienvenue"
              options={{ headerShown: false, gestureEnabled: false, presentation: 'fullScreenModal' }}
            />
          </Stack>
          {/* Compte obligatoire : sans session, seul l'écran de connexion / inscription est accessible. */}
          <AuthGate />
          <OnboardingGate />
          </FollowsProvider>
        </FavoritesProvider>
        </LaunchFeaturesProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
