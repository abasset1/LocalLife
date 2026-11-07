import { Image } from 'expo-image';
import { StyleSheet, useColorScheme, View } from 'react-native';

import { Spacing } from '@/constants/theme';

/**
 * Logo Neiri : épingle en dégradé (« N » et point du « i ») suivie du logotype « NEiRI ». Images générées à
 * partir des SVG de `frontend/public` (voir docs/brand/README.md). `inverted` : logotype blanc, pour un fond
 * bleu nuit ; en mode sombre, le logotype est toujours clair.
 */
export function BrandMark({ inverted = false }: { inverted?: boolean }) {
  const dark = useColorScheme() === 'dark';
  const light = inverted || dark;
  return (
    <View style={styles.row} accessible accessibilityRole="image" accessibilityLabel="Neiri">
      <Image source={require('@/assets/images/neiri-mark.png')} style={styles.mark} contentFit="contain" />
      <Image
        source={
          light
            ? require('@/assets/images/neiri-wordmark-light.png')
            : require('@/assets/images/neiri-wordmark.png')
        }
        style={styles.wordmark}
        contentFit="contain"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  // Proportions des images sources : repère 240 × 288, logotype 688 × 216.
  mark: {
    width: 30,
    height: 36,
  },
  wordmark: {
    width: 76,
    height: 24,
  },
});
