import { Image } from 'expo-image';
import { StyleSheet, Text, useColorScheme, View } from 'react-native';

import { NightNeon, Spacing } from '@/constants/theme';
import { useNightMode } from '@/hooks/use-night-mode';

/**
 * Logo Neiri : épingle en dégradé (« N » et point du « i ») suivie du logotype « NEiRI ». Images générées à
 * partir des SVG de `frontend/public` (voir docs/brand/README.md). `inverted` : logotype blanc, pour un fond
 * bleu nuit ; en mode sombre, le logotype est toujours clair. Thème « Night » (carte Night) : le bouc néon à
 * lunettes remplace l'épingle, logotype clair et son nom, « The OutGoat », dessous, comme sur le site
 * (`frontend/public/neiri-night.webp`).
 */
/** Nom du bouc néon, affiché sous « NEiRI » en thème « Night » (comme sur le site). */
export const NIGHT_MASCOT_NAME = 'The OutGoat';

export function BrandMark({ inverted = false }: { inverted?: boolean }) {
  const dark = useColorScheme() === 'dark';
  const night = useNightMode();
  const light = inverted || dark || night;
  return (
    <View
      style={styles.row}
      accessible
      accessibilityRole="image"
      accessibilityLabel={night ? `Neiri Night, ${NIGHT_MASCOT_NAME}` : 'Neiri'}>
      {night ? (
        <Image source={require('@/assets/images/neiri-night.webp')} style={styles.nightMark} contentFit="contain" />
      ) : (
        <Image source={require('@/assets/images/neiri-mark.png')} style={styles.mark} contentFit="contain" />
      )}
      <View style={styles.text}>
        <Image
          source={
            light
              ? require('@/assets/images/neiri-wordmark-light.png')
              : require('@/assets/images/neiri-wordmark.png')
          }
          style={styles.wordmark}
          contentFit="contain"
        />
        {night ? <Text style={styles.nightCaption}>{NIGHT_MASCOT_NAME}</Text> : null}
      </View>
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
  text: {
    gap: 2,
  },
  // « The OutGoat » en magenta néon avec un halo, comme sur le site.
  nightCaption: {
    color: NightNeon.magenta,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.7,
    textShadowColor: 'rgba(255, 63, 209, 0.75)',
    textShadowRadius: 8,
    textShadowOffset: { width: 0, height: 0 },
  },
  wordmark: {
    width: 76,
    height: 24,
  },
});
