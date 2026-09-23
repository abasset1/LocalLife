import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type TextFieldProps = Omit<TextInputProps, 'style'> & {
  /** Libellé visible, aussi utilisé comme nom accessible du champ. */
  label: string;
};

/** Champ de saisie avec libellé, zone tactile ≥ 48 px. */
export function TextField({ label, ...inputProps }: TextFieldProps) {
  const theme = useTheme();

  return (
    <View style={styles.wrapper}>
      <ThemedText type="smallBold">{label}</ThemedText>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={theme.textSecondary}
        style={[
          styles.input,
          { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.textSecondary },
        ]}
        {...inputProps}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.one,
  },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: Spacing.three,
    fontSize: 16,
  },
});
