import { StyleSheet, Switch, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';

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
  return (
    <View style={styles.switchRow}>
      <ThemedText>{label}</ThemedText>
      <Switch accessibilityLabel={label} value={value} onValueChange={onValueChange} disabled={disabled} />
    </View>
  );
}

const styles = StyleSheet.create({
  switchRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
});
