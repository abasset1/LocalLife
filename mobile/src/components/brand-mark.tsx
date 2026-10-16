import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Spacing } from '@/constants/theme';

/** Logo texte de la maquette : carré vert « L » suivi de « LocalLife ». */
export function BrandMark({ inverted = false }: { inverted?: boolean }) {
  return (
    <View style={styles.row} accessible accessibilityLabel="LocalLife">
      <View style={[styles.mark, { backgroundColor: inverted ? '#FFFFFF' : Brand.green }]}>
        <ThemedText style={[styles.letter, { color: inverted ? Brand.green : '#FFFFFF' }]}>L</ThemedText>
      </View>
      <ThemedText style={[styles.name, inverted && { color: '#FFFFFF' }]}>LocalLife</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.half,
  },
  mark: {
    width: 34,
    height: 34,
    borderRadius: Radius.md - 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  letter: {
    fontSize: 18,
    lineHeight: 22,
    fontWeight: 900,
  },
  name: {
    fontSize: 21,
    lineHeight: 26,
    fontWeight: 800,
    letterSpacing: -0.6,
  },
});
