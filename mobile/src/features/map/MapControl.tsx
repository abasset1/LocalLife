import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Bouton flottant posé sur la carte (pastille blanche ombrée). `active` le passe en vert plein, pour
 * un réglage en cours (lieux affichés, rayon choisi). Sans libellé visible, c'est un bouton rond.
 */
export function MapControl({
  icon,
  label,
  accessibilityLabel,
  active = false,
  loading = false,
  onPress,
}: {
  icon: SymbolViewProps['name'];
  /** Libellé visible ; absent pour un bouton rond (icône seule). */
  label?: string;
  accessibilityLabel: string;
  active?: boolean;
  loading?: boolean;
  onPress: () => void;
}) {
  const theme = useTheme();
  const foreground = active ? theme.onPrimary : theme.primary;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ selected: active, busy: loading, disabled: loading }}
      disabled={loading}
      onPress={onPress}
      hitSlop={4}
      style={({ pressed }) => [
        styles.control,
        label ? styles.pill : styles.round,
        { backgroundColor: active ? theme.primary : theme.backgroundElement },
        Shadows.floating,
        pressed && styles.pressed,
      ]}>
      {loading ? (
        <ActivityIndicator color={foreground} />
      ) : (
        <SymbolView name={icon} tintColor={foreground} size={20} />
      )}
      {label ? (
        <ThemedText type="smallBold" style={{ color: active ? theme.onPrimary : theme.text }}>
          {label}
        </ThemedText>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  control: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  pill: {
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.pill,
  },
  round: {
    width: 56,
    height: 56,
    borderRadius: 28,
  },
  pressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
});
