import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import type { Place } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { sortPlacesForList } from './placeList';
import { placeTypeLabel } from './placeTypes';

/**
 * Liste « Lieux » (ADR-0011, § 2.4) : lieux publiés visibles sur la carte, par commune puis par nom. Un appui
 * ouvre l'aperçu du lieu, comme son marqueur.
 */
export function PlaceListPanel({ places, onSelect }: { places: Place[]; onSelect: (place: Place) => void }) {
  const theme = useTheme();
  return (
    <View style={[styles.panel, { backgroundColor: theme.background }]}>
      <ThemedText type="smallBold">Lieux visibles ({places.length})</ThemedText>
      <FlatList
        data={sortPlacesForList(places)}
        keyExtractor={(place) => String(place.id)}
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${item.name}, ${placeTypeLabel(item.placeType)}`}
            onPress={() => onSelect(item)}
            style={[styles.item, { borderColor: theme.backgroundElement }]}>
            <ThemedText type="small" numberOfLines={1}>
              🏛 {item.name}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
              {[placeTypeLabel(item.placeType), item.city].filter(Boolean).join(' · ')}
            </ThemedText>
          </Pressable>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    maxHeight: 320,
    marginBottom: Spacing.two,
    padding: Spacing.three,
    borderRadius: 12,
    gap: Spacing.two,
  },
  item: {
    paddingVertical: Spacing.two,
    borderBottomWidth: 1,
    minHeight: 44,
  },
});
