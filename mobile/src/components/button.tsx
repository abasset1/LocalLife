import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ButtonProps = {
  label: string;
  onPress: () => void;
  /** Affiche un indicateur et désactive le bouton pendant une action en cours. */
  loading?: boolean;
  disabled?: boolean;
  /**
   * `primary` : action principale (vert plein) ; `secondary` : action non principale (fond clair,
   * bordure) ; `ghost` : action discrète, sans fond ; `danger` : action destructive.
   */
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  /** Pictogramme placé avant le libellé (SF Symbol sur iOS, Material Symbol ailleurs). */
  icon?: SymbolViewProps['name'];
};

/** Bouton tactile (zone ≥ 48 px) avec rôle et état accessibles. */
export function Button({ label, onPress, loading = false, disabled = false, variant = 'primary', icon }: ButtonProps) {
  const theme = useTheme();
  const inactive = disabled || loading;

  const palette = {
    primary: { background: theme.primary, border: theme.primary, label: theme.onPrimary },
    secondary: { background: theme.backgroundElement, border: theme.border, label: theme.text },
    ghost: { background: 'transparent', border: 'transparent', label: theme.primary },
    danger: { background: theme.dangerSoft, border: theme.dangerSoft, label: theme.danger },
  }[variant];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: palette.background, borderColor: palette.border },
        pressed && styles.pressed,
        inactive && styles.inactive,
      ]}>
      {loading ? (
        <ActivityIndicator color={palette.label} />
      ) : (
        <View style={styles.content}>
          {icon ? <SymbolView name={icon} tintColor={palette.label} size={18} /> : null}
          <ThemedText type="smallBold" style={[styles.label, { color: palette.label }]}>
            {label}
          </ThemedText>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 50,
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.four,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  label: {
    fontSize: 15,
  },
  pressed: {
    opacity: 0.85,
    transform: [{ scale: 0.98 }],
  },
  inactive: {
    opacity: 0.5,
  },
});
