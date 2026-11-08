import { Image } from 'expo-image';
import { StyleSheet, useColorScheme, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useNightMode } from '@/hooks/use-night-mode';

/**
 * Logo Neiri : épingle en dégradé (« N » et point du « i ») suivie du logotype « NEiRI ». Images générées à
 * partir des SVG de `frontend/public` (voir docs/brand/README.md). `inverted` : logotype blanc, pour un fond
 * bleu nuit ; en mode sombre, le logotype est toujours clair. Thème « Night » (carte Night) : le bouc néon à
 * lunettes remplace l'épingle, logotype clair, comme sur le site (`frontend/public/neiri-night.webp`).
 */
export function BrandMark({ inverted = false }: { inverted?: boolean }) {
  const dark = useColorScheme() === 'dark';
  const night = useNightMode();
  const light = inverted || dark || night;
  return (
    <View
      style={styles.row}
      accessible
      accessibilityRole="image"
      accessibilityLabel={night ? 'Neiri Night' : 'Neiri'}>
      {night ? (
        <Image source={require('@/assets/images/neiri-night.webp')} style={styles.nightMark} contentFit="contain" />
      ) : (
        <Image source={require('@/assets/images/neiri-mark.png')} style={styles.mark} contentFit="contain" />
      )}
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
  // Bouc néon (image carrée), un peu plus grand que l'épingle, comme le logo Night du menu du site.
  nightMark: {
    width: 48,
    height: 48,
  },
  wordmark: {
    width: 76,
    height: 24,
  },
});
