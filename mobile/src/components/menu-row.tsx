import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Ligne de menu : pastille d'icône, libellé, description facultative et chevron. */
export function MenuRow({
  icon,
  label,
  description,
  onPress,
  last = false,
}: {
  icon: SymbolViewProps['name'];
  label: string;
  description?: string;
  onPress: () => void;
  /** Dernière ligne d'un groupe : pas de séparateur en dessous. */
  last?: boolean;
}) {
  const theme = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={description}
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        !last && { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: theme.border },
        pressed && { backgroundColor: theme.backgroundSelected },
      ]}>
      <View style={[styles.icon, { backgroundColor: theme.primarySoft }]}>
        <SymbolView name={icon} tintColor={theme.primary} size={20} />
      </View>
      <View style={styles.text}>
        <ThemedText type="smallBold" style={styles.label}>
          {label}
        </ThemedText>
        {description ? (
          <ThemedText type="small" themeColor="textSecondary">
            {description}
          </ThemedText>
        ) : null}
      </View>
      <SymbolView
        name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }}
        tintColor={theme.textSecondary}
        size={16}
      />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: 64,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  icon: {
    width: 38,
    height: 38,
    borderRadius: Radius.md - 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    flex: 1,
    gap: Spacing.half,
  },
  label: {
    fontSize: 15,
  },
});
