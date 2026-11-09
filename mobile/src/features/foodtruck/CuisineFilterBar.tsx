import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { availableCuisines, CUISINE_TYPES } from './cuisines';

const ALL_LABEL = '🍽️ Toutes les cuisines';

/**
 * Filtre « type de cuisine » des food trucks (carte et liste), en liste déroulante comme sur le site : un bouton
 * affiche le choix courant et ouvre la liste « Toutes les cuisines » puis les types présents aujourd'hui ; le type
 * choisi reste proposé même s'il n'a plus de food truck. Rien n'est affiché sans type de cuisine renseigné.
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
  /** Sur la carte : bouton ombré. */
  floating?: boolean;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const present = availableCuisines(foodTrucks);
  const options = CUISINE_TYPES.filter((cuisine) => cuisine.code === selected || present.includes(cuisine));
  if (options.length === 0) return null;
  const items: { code: string | null; label: string }[] = [
    { code: null, label: ALL_LABEL },
    ...options.map((cuisine) => ({ code: cuisine.code, label: `${cuisine.emoji} ${cuisine.label}` })),
  ];
  const current = items.find((item) => item.code === selected) ?? items[0];
  const active = selected !== null;
  const close = () => setOpen(false);

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Type de cuisine : ${current.label}, modifier`}
        accessibilityState={{ expanded: open }}
        onPress={() => setOpen(true)}
        style={[
          styles.trigger,
          floating && Shadows.floating,
          active
            ? { backgroundColor: theme.primary, borderColor: theme.primary }
            : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        ]}>
        <ThemedText type="smallBold" numberOfLines={1} style={{ color: active ? theme.onPrimary : theme.text }}>
          {current.label}
        </ThemedText>
        <ThemedText type="smallBold" importantForAccessibility="no" style={{ color: active ? theme.onPrimary : theme.text }}>
          ▾
        </ThemedText>
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={close}>
        <Pressable accessibilityLabel="Fermer" style={styles.backdrop} onPress={close}>
          <Pressable
            accessibilityViewIsModal
            onPress={() => undefined}
            style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="smallBold" accessibilityRole="header" style={styles.title}>
              Type de cuisine
            </ThemedText>
            <ScrollView accessibilityRole="radiogroup" accessibilityLabel="Type de cuisine">
              {items.map((item) => {
                const isSelected = item.code === selected;
                return (
                  <Pressable
                    key={item.code ?? 'all'}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: isSelected }}
                    accessibilityLabel={item.label}
                    onPress={() => {
                      onChange(item.code);
                      close();
                    }}
                    style={({ pressed }) => [
                      styles.option,
                      { borderColor: theme.border },
                      pressed && { opacity: 0.6 },
                    ]}>
                    <ThemedText type={isSelected ? 'smallBold' : 'small'} style={styles.optionLabel}>
                      {item.label}
                    </ThemedText>
                    <View style={styles.check}>
                      {isSelected && (
                        <ThemedText type="smallBold" style={{ color: theme.primary }}>
                          ✓
                        </ThemedText>
                      )}
                    </View>
                  </Pressable>
                );
              })}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  trigger: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    minHeight: 36,
    maxWidth: '100%',
    marginVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.pill,
    borderWidth: 1,
  },
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    backgroundColor: 'rgba(23, 32, 29, 0.55)',
  },
  card: {
    width: '100%',
    maxWidth: 420,
    maxHeight: '70%',
    paddingVertical: Spacing.three,
    borderRadius: Radius.xl,
  },
  title: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 48,
    paddingHorizontal: Spacing.four,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  optionLabel: {
    flex: 1,
  },
  check: {
    width: 24,
    alignItems: 'flex-end',
  },
});
