import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { Shadows } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Bouton rond posé sur la carte (pastille blanche ombrée), icône seule : les contrôles de la carte
 * tiennent sur une seule rangée (FRONT-REV-02). `active` le passe en vert plein, pour un réglage en
 * cours (lieux affichés, rayon choisi) ; le détail est donné par `accessibilityLabel`. `badge` affiche un nombre
 * dans une pastille au coin du bouton (activités de la zone pour la liste), « 99+ » au-delà de 99.
 */
export function MapControl({
  icon,
  accessibilityLabel,
  active = false,
  loading = false,
  badge,
  onPress,
}: {
  icon: SymbolViewProps['name'];
  accessibilityLabel: string;
  active?: boolean;
  loading?: boolean;
  badge?: number;
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
      {badge !== undefined && badge > 0 ? (
        <View style={[styles.badge, { backgroundColor: theme.accent }]} importantForAccessibility="no-hide-descendants">
          <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
        </View>
      ) : null}
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
  badge: {
    position: 'absolute',
    top: -4,
    right: -6,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 4,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '800',
  },
  pressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
});
