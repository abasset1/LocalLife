import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { Shadows } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Bouton rond posé sur la carte (pastille blanche ombrée), icône seule : les contrôles de la carte
 * tiennent sur une seule rangée (FRONT-REV-02). `active` le passe en vert plein, pour un réglage en
 * cours (lieux affichés, rayon choisi) ; le détail est donné par `accessibilityLabel`.
 */
export function MapControl({
  icon,
  accessibilityLabel,
  active = false,
  loading = false,
  onPress,
}: {
  icon: SymbolViewProps['name'];
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
        { backgroundColor: active ? theme.primary : theme.backgroundElement },
        Shadows.floating,
        pressed && styles.pressed,
      ]}>
      {loading ? (
        <ActivityIndicator color={foreground} />
      ) : (
        <SymbolView name={icon} tintColor={foreground} size={22} />
      )}
    </Pressable>
  );
}

/** Cible tactile minimale (44 px) : sept boutons tiennent sur une rangée d'un écran de 360 px. */
const SIZE = 44;

const styles = StyleSheet.create({
  control: {
    width: SIZE,
    height: SIZE,
    borderRadius: SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
});
