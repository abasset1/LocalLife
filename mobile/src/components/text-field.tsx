import { SymbolView } from 'expo-symbols';
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type TextFieldProps = Omit<TextInputProps, 'style'> & {
  /** Libellé visible, aussi utilisé comme nom accessible du champ. */
  label: string;
  /**
   * Avec `secureTextEntry` : bouton « œil » dans le champ pour afficher ou masquer la saisie
   * (vérifier le mot de passe tapé).
   */
  revealable?: boolean;
};

/** Champ de saisie avec libellé, zone tactile ≥ 48 px ; la bordure passe au vert pendant la saisie. */
export function TextField({
  label,
  onFocus,
  onBlur,
  multiline,
  revealable = false,
  secureTextEntry,
  ...inputProps
}: TextFieldProps) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const canReveal = revealable && secureTextEntry === true;

  return (
    <View style={styles.wrapper}>
      <ThemedText type="smallBold">{label}</ThemedText>
      <View>
        <TextInput
          accessibilityLabel={label}
          placeholderTextColor={theme.textSecondary}
          selectionColor={theme.primary}
          multiline={multiline}
          textAlignVertical={multiline ? 'top' : 'center'}
          secureTextEntry={secureTextEntry && !(canReveal && revealed)}
          onFocus={(event) => {
            setFocused(true);
            onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            onBlur?.(event);
          }}
          style={[
            styles.input,
            multiline && styles.multiline,
            canReveal && styles.withReveal,
            {
              color: theme.text,
              backgroundColor: theme.backgroundElement,
              borderColor: focused ? theme.primary : theme.border,
            },
          ]}
          {...inputProps}
        />
        {canReveal && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={revealed ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
            accessibilityState={{ selected: revealed }}
            hitSlop={8}
            onPress={() => setRevealed((value) => !value)}
            style={styles.reveal}>
            <SymbolView
              name={
                revealed
                  ? { ios: 'eye.slash', android: 'visibility_off', web: 'visibility_off' }
                  : { ios: 'eye', android: 'visibility', web: 'visibility' }
              }
              tintColor={theme.textSecondary}
              size={22}
            />
          </Pressable>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.two,
  },
  input: {
    minHeight: 50,
    borderWidth: 1.5,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    fontSize: 16,
  },
  multiline: {
    minHeight: 110,
    paddingTop: Spacing.three,
    paddingBottom: Spacing.three,
  },
  withReveal: {
    paddingRight: 52,
  },
  reveal: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    width: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
