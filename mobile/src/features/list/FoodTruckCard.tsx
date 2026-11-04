import { Pressable, StyleSheet, View } from 'react-native';

import type { FoodTruck } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { RemoteImage } from '@/components/remote-image';
import { cuisineLabel, markerOf } from '@/features/foodtruck/cuisines';
import { photoUri } from '@/features/foodtruck/photo';
import { describeToday, spotAddressLine } from '@/features/foodtruck/presence';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte d'un food truck de l'onglet « Food trucks » (ADR-0013) : une présence du jour — photo, pastille, cuisine,
 * nom, description, emplacement et horaires, distance. Un appui ouvre la fiche du food truck et son planning.
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
  const address = spotAddressLine(foodTruck);
  const marker = markerOf(foodTruck);
  const cuisine = [cuisineLabel(foodTruck.cuisineType), foodTruck.category].filter(Boolean).join(' · ');

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={['Food truck', foodTruck.name, cuisine, meta, address].filter(Boolean).join(', ')}
      accessibilityHint="Ouvre la fiche du food truck"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}>
      <RemoteImage url={photoUri(foodTruck.photoUrl)} style={styles.photo} />
      <View style={[styles.accent, { backgroundColor: theme.accentSoft }]}>
        <View style={[styles.marker, { backgroundColor: marker.color }]} importantForAccessibility="no">
          <ThemedText style={styles.markerEmoji}>{marker.emoji}</ThemedText>
        </View>
        <ThemedText type="eyebrow" numberOfLines={1} style={styles.category}>
          {cuisine}
        </ThemedText>
      </View>
      <View style={styles.body}>
        <ThemedText type="heading" numberOfLines={2}>
          {foodTruck.name}
        </ThemedText>
        {foodTruck.description ? (
          <ThemedText type="small" numberOfLines={2}>
            {foodTruck.description}
          </ThemedText>
        ) : null}
        {address ? (
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
            🏠 {address}
          </ThemedText>
        ) : null}
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
  photo: {
    height: 150,
  },
  marker: {
    width: 28,
    height: 28,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  markerEmoji: {
    fontSize: 14,
    lineHeight: 18,
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
