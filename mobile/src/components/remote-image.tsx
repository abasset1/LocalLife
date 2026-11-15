import { Image, type ImageStyle } from 'expo-image';
import { useState, type ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { isSafeWebUrl } from '@/features/activity/format';
import { photoUri } from '@/features/foodtruck/photo';
import { useTheme } from '@/hooks/use-theme';

/**
 * Photo distante (source externe, ou image servie par l'API sous un chemin `/api/…`) : seules les URL http(s)
 * sont chargées. Sans photo,
 * ou si elle ne se charge pas, rien n'est affiché (`fallback` facultatif à la place).
 */
export function RemoteImage({
  url,
  alt,
  style,
  fallback = null,
}: {
  url: string | null | undefined;
  alt?: string | null;
  style: StyleProp<ViewStyle>;
  fallback?: ReactNode;
}) {
  const theme = useTheme();
  const [failed, setFailed] = useState<string | null>(null);
  // Image servie par l'API (ajoutée par un contributeur) : chemin relatif, complété par l'adresse du serveur.
  const resolved = url?.startsWith('/api/') ? photoUri(url) : url;
  if (!isSafeWebUrl(resolved) || failed === resolved) return <>{fallback}</>;
  return (
    <View style={[style, styles.clip, { backgroundColor: theme.backgroundSelected }]}>
      <Image
        source={{ uri: resolved }}
        style={StyleSheet.absoluteFill as StyleProp<ImageStyle>}
        contentFit="cover"
        transition={200}
        accessibilityLabel={alt ?? undefined}
        accessible={Boolean(alt)}
        onError={() => setFailed(resolved)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  clip: {
    overflow: 'hidden',
  },
});
