import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { deleteActivityPhoto, uploadActivityPhoto } from '@/api';
import { authorizationHeaders } from '@/api/http';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { photoUri } from '@/features/foodtruck/photo';
import { useTheme } from '@/hooks/use-theme';

/** Image du formulaire : inchangée, nouvelle (envoyée après l'enregistrement de l'activité) ou à retirer. */
export type PhotoChange = { kind: 'keep' } | { kind: 'new'; uri: string; base64: string } | { kind: 'remove' };

/** L'activité enregistrée, l'image suit : envoyée, retirée ou laissée telle quelle. */
export async function savePhotoChange(activityId: number, change: PhotoChange): Promise<void> {
  if (change.kind === 'new') await uploadActivityPhoto(activityId, change.base64);
  if (change.kind === 'remove') await deleteActivityPhoto(activityId);
}

/** Taille acceptée par le serveur : 3 Mo une fois décodée (4 caractères base64 pour 3 octets). */
const MAX_BYTES = 3 * 1024 * 1024;

/**
 * Image facultative d'une activité : choisie dans la galerie (recadrée en 4:3, compressée), affichée en aperçu,
 * remplaçable ou retirée. L'image déjà enregistrée d'une contribution en attente n'est servie qu'à son auteur :
 * elle est chargée avec le jeton de la session.
 */
export function ActivityPhotoField({
  currentPhotoUrl,
  change,
  onChange,
  onError,
}: {
  currentPhotoUrl: string | null | undefined;
  change: PhotoChange;
  onChange: (change: PhotoChange) => void;
  onError: (message: string) => void;
}) {
  const theme = useTheme();
  const [headers, setHeaders] = useState<Record<string, string>>({});
  const savedUri = photoUri(currentPhotoUrl);
  const shownUri = change.kind === 'new' ? change.uri : change.kind === 'remove' ? null : savedUri;

  useEffect(() => {
    if (!savedUri) return;
    let cancelled = false;
    void authorizationHeaders().then((value) => {
      if (!cancelled) setHeaders(value);
    });
    return () => {
      cancelled = true;
    };
  }, [savedUri]);

  async function pick() {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      onError('Autorisez l’accès à vos photos pour ajouter une image.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [4, 3],
      quality: 0.6,
      base64: true,
    });
    const asset = result.canceled ? null : result.assets[0];
    if (!asset?.base64) return;
    if ((asset.base64.length * 3) / 4 > MAX_BYTES) {
      onError('Cette image est trop lourde (3 Mo au plus) : recadrez-la ou choisissez-en une autre.');
      return;
    }
    onChange({ kind: 'new', uri: asset.uri, base64: asset.base64 });
  }

  return (
    <View style={styles.field}>
      <ThemedText type="smallBold">Image (facultatif)</ThemedText>
      <View style={[styles.frame, { borderColor: theme.border, backgroundColor: theme.sand }]}>
        {shownUri ? (
          <Image
            source={change.kind === 'new' ? { uri: shownUri } : { uri: shownUri, headers }}
            style={styles.image}
            contentFit="cover"
            accessibilityLabel="Image de l’activité"
          />
        ) : (
          <View style={styles.empty} importantForAccessibility="no">
            <ThemedText style={styles.emptyIcon}>📷</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Aucune image
            </ThemedText>
          </View>
        )}
      </View>
      <View style={styles.row}>
        <View style={styles.rowItem}>
          <Button
            label={shownUri ? 'Changer l’image' : 'Ajouter une image'}
            variant="secondary"
            icon={{ ios: 'photo', android: 'image', web: 'image' }}
            onPress={() => void pick()}
          />
        </View>
        {shownUri ? (
          <Button
            label="Retirer"
            variant="ghost"
            onPress={() => onChange(savedUri ? { kind: 'remove' } : { kind: 'keep' })}
          />
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  field: {
    gap: Spacing.two,
  },
  frame: {
    height: 170,
    overflow: 'hidden',
    borderRadius: Radius.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
  },
  emptyIcon: {
    fontSize: 30,
    lineHeight: 38,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  rowItem: {
    flex: 1,
  },
});
