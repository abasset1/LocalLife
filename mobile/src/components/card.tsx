import { StyleSheet, View, type ViewProps } from 'react-native';

import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type CardProps = ViewProps & {
  /** `floating` : ombre plus marquée, pour un panneau posé sur la carte. */
  elevation?: 'card' | 'floating' | 'none';
};

/** Surface blanche arrondie, bordée et légèrement ombrée (cartes de la maquette). */
export function Card({ style, elevation = 'card', ...rest }: CardProps) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        elevation !== 'none' && Shadows[elevation],
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    padding: Spacing.three,
    gap: Spacing.two,
  },
});
