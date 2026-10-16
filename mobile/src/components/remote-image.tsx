import { Image, type ImageStyle } from 'expo-image';
import { useState, type ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { isSafeWebUrl } from '@/features/activity/format';
import { useTheme } from '@/hooks/use-theme';

/**
 * Photo distante (fournie par une source externe) : seules les URL http(s) sont chargées. Sans photo,
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
  if (!isSafeWebUrl(url) || failed === url) return <>{fallback}</>;
  return (
    <View style={[style, styles.clip, { backgroundColor: theme.backgroundSelected }]}>
      <Image
        source={{ uri: url }}
        style={StyleSheet.absoluteFill as StyleProp<ImageStyle>}
        contentFit="cover"
        transition={200}
        accessibilityLabel={alt ?? undefined}
        accessible={Boolean(alt)}
        onError={() => setFailed(url)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  clip: {
    overflow: 'hidden',
  },
});
