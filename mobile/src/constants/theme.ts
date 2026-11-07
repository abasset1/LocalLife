/**
 * Charte visuelle Neiri (docs/brand/README.md) : dégradé orange → rose → violet, bleu nuit, lavande.
 * Les couleurs existent en mode clair et sombre ; les écrans les lisent
 * via `useTheme()` plutôt que de coder des valeurs en dur.
 */

import '@/global.css';

import { Platform, type ViewStyle } from 'react-native';

export const Brand = {
  navy: '#1C2150',
  navy2: '#2D3378',
  /** Rose des actions principales (contraste AA sur blanc). */
  pink: '#D6295F',
  pinkBright: '#EC3B6E',
  orange: '#FBA91E',
  purple: '#7B2CB5',
  lavender: '#8C84D8',
  lavenderSoft: '#E4E0F7',
  paper: '#FBFAFF',
  ink: '#1C2150',
  /** Dégradé de la marque (épingle, icône), du haut-gauche vers le bas-droite. */
  gradient: ['#FBA91E', '#EC3B6E', '#7B2CB5'],
} as const;

export const Colors = {
  light: {
    text: Brand.ink,
    background: '#F7F6FC',
    backgroundElement: '#FFFFFF',
    backgroundSelected: '#ECE9FB',
    textSecondary: '#646887',
    border: '#E6E4F2',
    danger: '#B3261E',
    dangerSoft: '#FBE9E7',
    success: '#1F7A55',
    successSoft: '#E3F2EA',
    warning: '#A15C07',
    warningSoft: '#FDF1DC',
    primary: Brand.navy,
    primarySoft: '#E8E7F6',
    onPrimary: '#FFFFFF',
    accent: Brand.pink,
    accentSoft: '#FDE6EE',
    sand: Brand.lavenderSoft,
  },
  dark: {
    text: '#F1F0FA',
    background: '#0F1130',
    backgroundElement: '#191C42',
    backgroundSelected: '#252960',
    textSecondary: '#A9ABCB',
    border: '#2B2F63',
    danger: '#FF8A80',
    dangerSoft: '#3A1F1C',
    success: '#6FD0A3',
    successSoft: '#173226',
    warning: '#F4B56C',
    warningSoft: '#3A2A14',
    primary: '#6F67D8',
    primarySoft: '#272B63',
    onPrimary: '#FFFFFF',
    accent: '#F25A86',
    accentSoft: '#40192A',
    sand: '#1D2048',
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

/** Ombres douces et teintées de bleu nuit (iOS : shadow*, Android : elevation). */
export const Shadows = {
  card: {
    shadowColor: Brand.navy,
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
