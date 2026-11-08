import { ActivityIndicator, Pressable, ScrollView, StyleSheet, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Bouton de la carte avec son libellé écrit (« 📅 Activités », « 🗓 Ce week-end », « Liste (37) »), comme sur le site :
 * chaque commande dit ce qu'elle fait, au lieu d'une rangée d'icônes à deviner. `active` le remplit (onglet affiché,
 * filtre en cours, panneau ouvert).
 */
export function MapChip({
  label,
  accessibilityLabel,
  active = false,
  loading = false,
  role = 'button',
  onPress,
  style,
}: {
  label: string;
  accessibilityLabel?: string;
  active?: boolean;
  loading?: boolean;
  /** `tab` pour les onglets de contenu (annoncés comme onglets). */
  role?: 'button' | 'tab';
  onPress: () => void;
  style?: ViewStyle;
}) {
  const theme = useTheme();
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
        active
          ? { backgroundColor: theme.primary, borderColor: theme.primary }
          : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        styles.shadow,
        pressed && styles.pressed,
        style,
      ]}>
      {loading ? <ActivityIndicator size="small" color={active ? theme.onPrimary : theme.primary} /> : null}
      <ThemedText type="smallBold" numberOfLines={1} style={{ color: active ? theme.onPrimary : theme.text }}>
        {label}
      </ThemedText>
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
