import { Pressable, ScrollView, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { availableCuisines, CUISINE_TYPES } from './cuisines';

/**
 * Filtre « type de cuisine » des food trucks (carte et liste) : « Toutes » puis les types présents aujourd'hui ; le
 * type choisi reste proposé même s'il n'a plus de food truck. Rien n'est affiché sans type de cuisine renseigné.
 */
export function CuisineFilterBar({
  foodTrucks,
  selected,
  onChange,
  floating = false,
}: {
  foodTrucks: readonly { cuisineType?: string | null }[];
  /** Code choisi, `null` = toutes les cuisines. */
  selected: string | null;
  onChange: (code: string | null) => void;
  /** Sur la carte : pastilles ombrées, sans fond. */
  floating?: boolean;
}) {
  const theme = useTheme();
  const present = availableCuisines(foodTrucks);
  const options = CUISINE_TYPES.filter((cuisine) => cuisine.code === selected || present.includes(cuisine));
  if (options.length === 0) return null;
  const chips: { code: string | null; label: string }[] = [
    { code: null, label: '🍽️ Toutes' },
    ...options.map((cuisine) => ({ code: cuisine.code, label: `${cuisine.emoji} ${cuisine.label}` })),
  ];
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      accessibilityRole="radiogroup"
      accessibilityLabel="Type de cuisine"
      style={styles.scroll}
      contentContainerStyle={styles.row}
      keyboardShouldPersistTaps="handled">
      {chips.map((chip) => {
        const active = chip.code === selected;
        return (
          <Pressable
            key={chip.code ?? 'all'}
            accessibilityRole="radio"
            accessibilityState={{ selected: active }}
            accessibilityLabel={chip.label}
            onPress={() => onChange(chip.code)}
            style={[
              styles.chip,
              floating && Shadows.floating,
              active
                ? { backgroundColor: theme.primary, borderColor: theme.primary }
                : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
            ]}>
            <ThemedText type="smallBold" style={{ color: active ? theme.onPrimary : theme.text }}>
              {chip.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    alignSelf: 'stretch',
    flexGrow: 0,
  },
  row: {
    gap: Spacing.two,
    paddingVertical: Spacing.one,
  },
  chip: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.pill,
    borderWidth: 1,
  },
});
