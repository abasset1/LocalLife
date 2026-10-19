import { Pressable, StyleSheet, View } from 'react-native';

import type { FoodTruck } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { describeToday } from '@/features/foodtruck/presence';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte d'un food truck de l'onglet « Food trucks » (ADR-0013) : une présence du jour — cuisine, nom,
 * emplacement et horaires, distance. Un appui ouvre la fiche du food truck et son planning.
 */
export function FoodTruckCard({
  foodTruck,
  distance,
  onPress,
}: {
  foodTruck: FoodTruck;
  /** Distance déjà mise en forme (« 2,4 km »). */
  distance?: string | null;
  onPress: () => void;
}) {
  const theme = useTheme();
  const presence = describeToday(foodTruck);
  const meta = [presence, distance].filter(Boolean).join(' · ');

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={['Food truck', foodTruck.name, foodTruck.category, meta].filter(Boolean).join(', ')}
      accessibilityHint="Ouvre la fiche du food truck"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}>
      <View style={[styles.accent, { backgroundColor: theme.accentSoft }]}>
        <ThemedText importantForAccessibility="no">🚚</ThemedText>
        <ThemedText type="eyebrow" numberOfLines={1} style={styles.category}>
          {foodTruck.category}
        </ThemedText>
      </View>
      <View style={styles.body}>
        <ThemedText type="heading" numberOfLines={2}>
          {foodTruck.name}
        </ThemedText>
        <View style={styles.footer}>
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={2} style={styles.meta}>
            {meta ? `📍 ${meta}` : ''}
          </ThemedText>
          <ThemedText type="smallBold" themeColor="primary">
            Voir →
          </ThemedText>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
    ...Shadows.card,
  },
  pressed: {
    opacity: 0.9,
    transform: [{ scale: 0.99 }],
  },
  accent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    minHeight: 48,
  },
  category: {
    flex: 1,
  },
  body: {
    padding: Spacing.three,
    gap: Spacing.one + Spacing.half,
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  meta: {
    flex: 1,
  },
});
