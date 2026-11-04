import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { Pressable, StyleSheet, View } from 'react-native';

import type { OwnedFoodTruck, ProfileInput } from '@/api';
import { deleteFoodTruckPhoto, uploadFoodTruckPhoto } from '@/api';
import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import {
  CUISINE_TYPES,
  DEFAULT_MARKER_COLOR,
  MARKER_COLORS,
  MARKER_EMOJIS,
  markerOf,
} from '@/features/foodtruck/cuisines';
import { photoUri } from '@/features/foodtruck/photo';
import { ChipGroup } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

/** Profil saisi : champs texte, type de cuisine et pastille (vides = non renseignés). */
export type ProfileForm = Required<{ [K in keyof ProfileInput]: string }>;

export const EMPTY_PROFILE: ProfileForm = {
  name: '',
  category: '',
  description: '',
  contact: '',
  cuisineType: '',
  markerEmoji: '',
  markerColor: '',
};

/** Photo du formulaire : inchangée, nouvelle (envoyée après l'enregistrement du profil) ou à retirer. */
export type PhotoChange = { kind: 'keep' } | { kind: 'new'; uri: string; base64: string } | { kind: 'remove' };

export function profileFormOf(truck: OwnedFoodTruck): ProfileForm {
  return {
    name: truck.name,
    category: truck.category,
    description: truck.description ?? '',
    contact: truck.contact ?? '',
    cuisineType: truck.cuisineType ?? '',
    markerEmoji: truck.markerEmoji ?? '',
    markerColor: truck.markerColor ?? '',
  };
}

/** Corps envoyé au backend : les choix vides deviennent `null`. */
export function profileInputOf(form: ProfileForm): ProfileInput {
  return {
    ...form,
    cuisineType: form.cuisineType || null,
    markerEmoji: form.markerEmoji || null,
    markerColor: form.markerColor || null,
  };
}

/** Le profil enregistré, la photo suit : envoyée, retirée ou laissée telle quelle. */
export async function savePhoto(truckId: number, change: PhotoChange): Promise<void> {
  if (change.kind === 'new') await uploadFoodTruckPhoto(truckId, change.base64);
  if (change.kind === 'remove') await deleteFoodTruckPhoto(truckId);
}

/**
 * Champs du profil d'un food truck, à la création comme à la modification (mêmes champs que sur le site) : nom, type
 * de cuisine (filtre de la carte), spécialité, description, contact, photo et pastille de la carte.
 */
export function FoodTruckProfileFields({
  profile,
  onChange,
  currentPhotoUrl,
  photo,
  onPhotoChange,
  onError,
}: {
  profile: ProfileForm;
  onChange: (profile: ProfileForm) => void;
  /** Photo déjà enregistrée (`photoUrl` du backend), `null` sans photo ou pour un nouveau food truck. */
  currentPhotoUrl: string | null | undefined;
  photo: PhotoChange;
  onPhotoChange: (change: PhotoChange) => void;
  onError: (message: string) => void;
}) {
  const theme = useTheme();
  const marker = markerOf(profile);
  const shownPhoto =
    photo.kind === 'new' ? photo.uri : photo.kind === 'remove' ? null : photoUri(currentPhotoUrl);

  async function pickPhoto() {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      onError('Autorisez l’accès à vos photos pour en ajouter une.');
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
    // Le backend accepte 3 Mo au plus (4 caractères base64 pour 3 octets).
    if ((asset.base64.length * 3) / 4 > 3 * 1024 * 1024) {
      onError('Cette photo est trop lourde (3 Mo au plus) : recadrez-la ou choisissez-en une autre.');
      return;
    }
    onPhotoChange({ kind: 'new', uri: asset.uri, base64: asset.base64 });
  }

  return (
    <>
      <TextField
        label="Nom commercial"
        value={profile.name}
        onChangeText={(v) => onChange({ ...profile, name: v })}
        maxLength={255}
      />
      <ChipGroup
        label="Type de cuisine"
        single
        options={CUISINE_TYPES.map((cuisine) => ({ value: cuisine.code, label: `${cuisine.emoji} ${cuisine.label}` }))}
        selected={profile.cuisineType ? [profile.cuisineType] : []}
        onToggle={(code) => onChange({ ...profile, cuisineType: code })}
      />
      <TextField
        label="Spécialité"
        placeholder="Tacos maison, crêpes bretonnes…"
        value={profile.category}
        onChangeText={(v) => onChange({ ...profile, category: v })}
        maxLength={255}
      />
      <TextField
        label="Description"
        placeholder="Votre histoire, votre carte, vos produits…"
        value={profile.description}
        onChangeText={(v) => onChange({ ...profile, description: v })}
        multiline
        maxLength={2000}
      />
      <TextField
        label="Contact ou lien utile"
        placeholder="Téléphone, site, réseau social…"
        value={profile.contact}
        onChangeText={(v) => onChange({ ...profile, contact: v })}
        autoCapitalize="none"
        maxLength={512}
      />

      <View style={styles.section}>
        <ThemedText type="smallBold">Photo</ThemedText>
        {shownPhoto ? (
          <Image
            source={{ uri: shownPhoto }}
            style={[styles.photo, { backgroundColor: theme.backgroundSelected }]}
            contentFit="cover"
            accessibilityLabel="Photo du food truck"
          />
        ) : null}
        <View style={styles.row}>
          <Button
            label={shownPhoto ? 'Changer la photo' : 'Ajouter une photo'}
            variant="secondary"
            icon={{ ios: 'photo', android: 'image', web: 'image' }}
            onPress={() => void pickPhoto()}
          />
          {shownPhoto ? (
            <Button label="Retirer" variant="ghost" onPress={() => onPhotoChange({ kind: 'remove' })} />
          ) : null}
        </View>
      </View>

      <View style={styles.section}>
        <View style={styles.row}>
          <ThemedText type="smallBold">Pastille sur la carte</ThemedText>
          <View style={[styles.preview, { backgroundColor: marker.color }]} importantForAccessibility="no">
            <ThemedText style={styles.previewEmoji}>{marker.emoji}</ThemedText>
          </View>
        </View>
        <View style={styles.grid} accessibilityLabel="Emoji de la pastille">
          {['', ...MARKER_EMOJIS].map((emoji) => {
            const active = profile.markerEmoji === emoji;
            return (
              <Pressable
                key={emoji || 'default'}
                accessibilityRole="radio"
                accessibilityState={{ selected: active }}
                accessibilityLabel={emoji ? `Emoji ${emoji}` : 'Emoji du type de cuisine'}
                onPress={() => onChange({ ...profile, markerEmoji: emoji })}
                style={[
                  styles.swatch,
                  { borderColor: active ? theme.primary : theme.border, backgroundColor: theme.backgroundElement },
                  active && styles.swatchActive,
                ]}>
                <ThemedText style={styles.swatchEmoji}>{emoji || '∅'}</ThemedText>
              </Pressable>
            );
          })}
        </View>
        <View style={styles.grid} accessibilityLabel="Couleur de la pastille">
          {MARKER_COLORS.map((color) => {
            const active = (profile.markerColor || DEFAULT_MARKER_COLOR) === color;
            return (
              <Pressable
                key={color}
                accessibilityRole="radio"
                accessibilityState={{ selected: active }}
                accessibilityLabel={`Couleur ${color}`}
                onPress={() => onChange({ ...profile, markerColor: color === DEFAULT_MARKER_COLOR ? '' : color })}
                style={[
                  styles.swatch,
                  { backgroundColor: color, borderColor: active ? theme.text : 'transparent' },
                  active && styles.swatchActive,
                ]}
              />
            );
          })}
        </View>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  section: {
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  photo: {
    height: 180,
    borderRadius: Radius.md,
  },
  preview: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderWidth: 2.5,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewEmoji: {
    fontSize: 18,
    lineHeight: 24,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  swatch: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  swatchActive: {
    borderWidth: 3,
  },
  swatchEmoji: {
    fontSize: 18,
    lineHeight: 24,
  },
});
