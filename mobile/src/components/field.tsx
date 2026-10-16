import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';

/** Libellé + valeur d'une fiche, lus d'un bloc par les lecteurs d'écran. */
export function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.field} accessible accessibilityLabel={`${label} : ${value}`}>
      <ThemedText type="eyebrow" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText>{value}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  field: {
    gap: Spacing.one,
  },
});
