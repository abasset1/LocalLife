/**
 * Charte visuelle de l'application mobile, alignée sur la maquette d'accueil
 * (`LocalLife_home_mockup.html`) : vert forêt, corail, sable et papier.
 * Les couleurs existent en mode clair et sombre ; les écrans les lisent via
 * `useTheme()` plutôt que de coder des valeurs en dur.
 */

import '@/global.css';

import { Platform, type ViewStyle } from 'react-native';

export const Brand = {
  green: '#173F35',
  green2: '#245447',
  coral: '#E86A4A',
  sand: '#F3EBDD',
  paper: '#FCFBF7',
  ink: '#17201D',
} as const;

export const Colors = {
  light: {
    text: Brand.ink,
    background: '#F7F4EC',
    backgroundElement: '#FFFFFF',
    backgroundSelected: '#EFE8DA',
    textSecondary: '#68736E',
    border: '#E7E3D9',
    danger: '#B3261E',
    dangerSoft: '#FBE9E7',
    success: '#1F7A55',
    successSoft: '#E3F2EA',
    warning: '#A15C07',
    warningSoft: '#FDF1DC',
    primary: Brand.green,
    primarySoft: '#E2ECE7',
    onPrimary: '#FFFFFF',
    accent: Brand.coral,
    accentSoft: '#FCE8E1',
    sand: Brand.sand,
  },
  dark: {
    text: '#F1EEE7',
    background: '#0E1412',
    backgroundElement: '#18211E',
    backgroundSelected: '#23302B',
    textSecondary: '#A6B1AC',
    border: '#2A3632',
    danger: '#FF8A80',
    dangerSoft: '#3A1F1C',
    success: '#6FD0A3',
    successSoft: '#173226',
    warning: '#F4B56C',
    warningSoft: '#3A2A14',
    primary: '#2F7A64',
    primarySoft: '#1C3530',
    onPrimary: '#FFFFFF',
    accent: '#F07D5E',
    accentSoft: '#3B231C',
    sand: '#1F2925',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;
export type Theme = { [K in ThemeColor]: string };

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: 'var(--font-display)',
    serif: 'var(--font-serif)',
    rounded: 'var(--font-rounded)',
    mono: 'var(--font-mono)',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

/** Arrondis : champs et boutons (`md`), cartes (`lg`), panneaux flottants (`xl`). */
export const Radius = {
  sm: 8,
  md: 14,
  lg: 20,
  xl: 28,
  pill: 999,
} as const;

/** Ombres douces et teintées de vert, comme sur la maquette (iOS : shadow*, Android : elevation). */
export const Shadows = {
  card: {
    shadowColor: Brand.green,
    shadowOpacity: 0.08,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
  floating: {
    shadowColor: '#000000',
    shadowOpacity: 0.16,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 6,
  },
} satisfies Record<string, ViewStyle>;

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
