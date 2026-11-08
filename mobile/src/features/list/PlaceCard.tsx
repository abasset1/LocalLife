import { Pressable, StyleSheet, View } from 'react-native';

import type { Place } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { placeTypeLabel } from '@/features/map/placeTypes';
import { useTheme } from '@/hooks/use-theme';

/** Couleur des lieux sur la carte (`markers.tsx`), reprise ici pour qu'on les reconnaisse dans la liste. */
const PLACE_COLOR = '#6B5CA5';

/**
 * Carte d'un lieu permanent dans la liste (univers « Lieux » et « Tous », issue #226) : type, nom, commune, distance.
 * Un lieu n'a pas de fiche dédiée : un appui cadre la carte dessus.
 */
export function PlaceCard({ place, distance, onPress }: { place: Place; distance: string; onPress: () => void }) {
  const theme = useTheme();
  const type = placeTypeLabel(place.placeType);
  const meta = [place.city, distance].filter(Boolean).join(' · ');
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={['Lieu', place.name, type, meta].filter(Boolean).join(', ')}
      accessibilityHint="Montre ce lieu sur la carte"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}>
      <View style={styles.marker} importantForAccessibility="no">
        <ThemedText style={styles.markerIcon}>🏛</ThemedText>
      </View>
      <View style={styles.body}>
        <ThemedText type="eyebrow" numberOfLines={1}>
          {type}
        </ThemedText>
        <ThemedText type="heading" numberOfLines={2}>
          {place.name}
        </ThemedText>
        {place.description ? (
          <ThemedText type="small" numberOfLines={2}>
            {place.description}
          </ThemedText>
        ) : null}
        <View style={styles.footer}>
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={1} style={styles.meta}>
            📍 {meta}
          </ThemedText>
          <ThemedText type="smallBold" themeColor="primary">
            Sur la carte →
          </ThemedText>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    gap: Spacing.three,
    padding: Spacing.three,
    borderRadius: Radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    ...Shadows.card,
  },
  pressed: {
    opacity: 0.9,
    transform: [{ scale: 0.99 }],
  },
  marker: {
    width: 40,
    height: 40,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#ffffff',
    backgroundColor: PLACE_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  markerIcon: {
    fontSize: 18,
    lineHeight: 22,
  },
  body: {
    flex: 1,
    gap: Spacing.one,
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
