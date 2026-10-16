import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';

/** En-tête d'écran : sur-titre corail, grand titre et texte d'introduction facultatif. */
export function ScreenHeader({ eyebrow, title, lead }: { eyebrow?: string; title: string; lead?: string }) {
  return (
    <View style={styles.header}>
      {eyebrow ? <ThemedText type="eyebrow">{eyebrow}</ThemedText> : null}
      <ThemedText type="title" accessibilityRole="header">
        {title}
      </ThemedText>
      {lead ? <ThemedText themeColor="textSecondary">{lead}</ThemedText> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    gap: Spacing.two,
  },
});
