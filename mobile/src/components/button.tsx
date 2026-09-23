import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Colors, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ButtonProps = {
  label: string;
  onPress: () => void;
  /** Affiche un indicateur et désactive le bouton pendant une action en cours. */
  loading?: boolean;
  disabled?: boolean;
  /** `secondary` : bouton discret (bordure), pour les actions non principales. */
  variant?: 'primary' | 'secondary';
};

/** Bouton tactile (zone ≥ 48 px) avec rôle et état accessibles. */
export function Button({ label, onPress, loading = false, disabled = false, variant = 'primary' }: ButtonProps) {
  const theme = useTheme();
  const inactive = disabled || loading;
  const primary = variant === 'primary';

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        primary ? { backgroundColor: Colors.light.primary } : { borderWidth: 1, borderColor: theme.textSecondary },
        (pressed || inactive) && styles.dimmed,
      ]}>
      {loading ? (
        <ActivityIndicator color={primary ? '#ffffff' : theme.text} />
      ) : (
        <ThemedText type="smallBold" style={primary ? styles.primaryLabel : undefined}>
          {label}
        </ThemedText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    borderRadius: 8,
    paddingHorizontal: Spacing.four,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dimmed: {
    opacity: 0.6,
  },
  primaryLabel: {
    color: '#ffffff',
  },
});
