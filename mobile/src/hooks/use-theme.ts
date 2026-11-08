/**
 * Learn more about light and dark modes:
 * https://docs.expo.dev/guides/color-schemes/
 */

import { Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useNightMode } from '@/hooks/use-night-mode';

export type ThemeName = 'light' | 'dark' | 'night';

/** « Night » tant que la carte Night est choisie, sinon clair ou sombre selon le système. */
export function themeNameFor(scheme: string | null | undefined, night: boolean): ThemeName {
  if (night) return 'night';
  return scheme === 'dark' ? 'dark' : 'light';
}

/** Nom du thème affiché. */
export function useThemeName(): ThemeName {
  return themeNameFor(useColorScheme(), useNightMode());
}

export function useTheme() {
  return Colors[useThemeName()];
}
