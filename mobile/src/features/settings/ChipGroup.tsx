import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Colors, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export interface ChipOption {
  value: string;
  label: string;
}

interface ChipGroupProps {
  label: string;
  options: readonly ChipOption[];
  selected: readonly string[];
  onToggle: (value: string) => void;
  /** Choix unique (rayon) plutôt que multiple (catégories) : annoncé comme bouton radio. */
  single?: boolean;
}

/** Puces sélectionnables (zone tactile ≥ 44 px), pour les catégories et le rayon. */
export function ChipGroup({ label, options, selected, onToggle, single = false }: ChipGroupProps) {
  const theme = useTheme();
  return (
    <View style={styles.wrapper} accessibilityRole={single ? 'radiogroup' : undefined}>
      <ThemedText type="smallBold">{label}</ThemedText>
      <View style={styles.chips}>
        {options.map((option) => {
          const active = selected.includes(option.value);
          return (
            <Pressable
              key={option.value}
              accessibilityRole={single ? 'radio' : 'checkbox'}
              accessibilityState={single ? { selected: active } : { checked: active }}
              accessibilityLabel={option.label}
              onPress={() => onToggle(option.value)}
              style={[
                styles.chip,
                active
                  ? { backgroundColor: Colors.light.primary, borderColor: Colors.light.primary }
                  : { borderColor: theme.textSecondary },
              ]}>
              <ThemedText type="small" style={active ? styles.activeLabel : undefined}>
                {option.label}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.two,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  chip: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderWidth: 1,
    borderRadius: 22,
  },
  activeLabel: {
    color: '#ffffff',
  },
});
