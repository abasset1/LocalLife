import { StyleSheet, Switch, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Ligne « interrupteur + libellé », zone tactile ≥ 48 px. */
export function ChannelSwitch({
  label,
  value,
  onValueChange,
  disabled,
}: {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  const theme = useTheme();
  return (
    <View style={styles.switchRow}>
      <ThemedText style={styles.label}>{label}</ThemedText>
      <Switch
        accessibilityLabel={label}
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
        trackColor={{ false: theme.backgroundSelected, true: theme.primary }}
        thumbColor="#FFFFFF"
        ios_backgroundColor={theme.backgroundSelected}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  switchRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.three,
  },
  label: {
    flex: 1,
  },
});
