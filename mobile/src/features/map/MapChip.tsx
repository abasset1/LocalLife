import { Image } from 'expo-image';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { NightNeon, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ChipIcon = SymbolViewProps['name'];

/**
 * Bouton de la carte avec son libellé écrit (« Tous », « Ce week-end », « Voir la liste (37) ») et, au besoin, un
 * pictogramme système. `active` le remplit (univers affiché, filtre en cours, panneau ouvert).
 *
 * `night` : raccourci de l'univers Night (issue #226). Il garde son logo officiel (le bouc néon) et son fond nuit
 * dans tous les thèmes, pour rester reconnaissable ; choisi, il s'allume en magenta néon.
 */
export function MapChip({
  label,
  accessibilityLabel,
  active = false,
  loading = false,
  role = 'button',
  icon,
  night = false,
  small = false,
  iconOnly = false,
  onPress,
  style,
}: {
  label: string;
  accessibilityLabel?: string;
  active?: boolean;
  loading?: boolean;
  /** `tab` pour les raccourcis d'univers (annoncés comme onglets). */
  role?: 'button' | 'tab';
  icon?: ChipIcon;
  night?: boolean;
  /** Filtres : bouton plus bas, sous les raccourcis. */
  small?: boolean;
  /** Pictogramme seul, sans texte (filtres de la carte) : `label` reste lu par les lecteurs d'écran. */
  iconOnly?: boolean;
  onPress: () => void;
  style?: ViewStyle;
}) {
  const theme = useTheme();
  const colors = night
    ? active
      ? { background: NightNeon.magenta, border: NightNeon.magenta, text: NightNeon.ink }
      : { background: NightNeon.ink, border: NightNeon.magenta, text: '#F3ECFF' }
    : active
      ? { background: theme.primary, border: theme.primary, text: theme.onPrimary }
      : { background: theme.backgroundElement, border: theme.border, text: theme.text };
  return (
    <Pressable
      accessibilityRole={role}
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={role === 'tab' ? { selected: active } : { selected: active, busy: loading }}
      disabled={loading}
      onPress={onPress}
      hitSlop={4}
      style={({ pressed }) => [
        styles.chip,
        small && styles.small,
        iconOnly && styles.iconOnly,
        night && styles.night,
        { backgroundColor: colors.background, borderColor: colors.border },
        styles.shadow,
        night && active && styles.nightGlow,
        pressed && styles.pressed,
        style,
      ]}>
      {loading ? <ActivityIndicator size="small" color={colors.text} /> : null}
      {night ? (
        <Image
          source={require('@/assets/images/neiri-night.webp')}
          style={styles.nightLogo}
          contentFit="contain"
          accessibilityIgnoresInvertColors
        />
      ) : icon && !loading ? (
        <SymbolView name={icon} tintColor={colors.text} size={iconOnly ? 20 : small ? 14 : 16} />
      ) : null}
      {iconOnly ? null : (
        <ThemedText type="smallBold" numberOfLines={1} style={{ color: colors.text }}>
          {label}
        </ThemedText>
      )}
    </Pressable>
  );
}

/** Rangée de boutons qui défile à l'horizontale quand elle dépasse la largeur de l'écran. */
export function MapChipRow({
  children,
  accessibilityLabel,
  tabs = false,
}: {
  children: React.ReactNode;
  accessibilityLabel: string;
  /** Onglets de contenu : annoncés comme une barre d'onglets. */
  tabs?: boolean;
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
      accessibilityLabel={accessibilityLabel}
      accessibilityRole={tabs ? 'tablist' : undefined}
      style={styles.row}
      contentContainerStyle={styles.rowContent}>
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.pill,
    borderWidth: StyleSheet.hairlineWidth,
  },
  // Ombre courte : elle tient dans la marge des rangées qui défilent (une ombre longue y serait coupée net).
  shadow: {
    shadowColor: '#000000',
    shadowOpacity: 0.14,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  // Rond de 44 points : cible tactile suffisante sans texte.
  iconOnly: {
    width: 44,
    minHeight: 44,
    paddingHorizontal: 0,
    justifyContent: 'center',
  },
  small: {
    minHeight: 34,
    paddingHorizontal: Spacing.three - Spacing.one,
  },
  // Le bouc touche presque le bord gauche, comme le logo Night du site.
  night: {
    paddingLeft: Spacing.one,
    borderWidth: 1,
  },
  nightLogo: {
    width: 30,
    height: 30,
  },
  nightGlow: {
    shadowColor: NightNeon.magenta,
    shadowOpacity: 0.8,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 0 },
  },
  pressed: {
    opacity: 0.85,
    transform: [{ scale: 0.97 }],
  },
  row: {
    alignSelf: 'stretch',
    flexGrow: 0,
    // La rangée déborde de la marge de l'écran pour défiler jusqu'au bord, et en hauteur pour ne pas couper
    // l'ombre des boutons (sinon une bande claire apparaît derrière la rangée).
    marginHorizontal: -Spacing.three,
    marginVertical: -Spacing.two,
  },
  rowContent: {
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three - Spacing.one,
  },
});
