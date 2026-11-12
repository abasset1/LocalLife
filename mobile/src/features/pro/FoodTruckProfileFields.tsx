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
  cuisineLabel,
  markerOf,
} from '@/features/foodtruck/cuisines';
import { photoUri } from '@/features/foodtruck/photo';
import { ChipGroup } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { FormSection, Hint } from './FormParts';

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
  instagram: '',
  facebook: '',
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
    instagram: truck.instagramUrl ?? '',
    facebook: truck.facebookUrl ?? '',
  };
}

/** Corps envoyé au backend : les choix vides deviennent `null`. */
export function profileInputOf(form: ProfileForm): ProfileInput {
  return {
    ...form,
    cuisineType: form.cuisineType || null,
    markerEmoji: form.markerEmoji || null,
    markerColor: form.markerColor || null,
    instagram: form.instagram.trim() || null,
    facebook: form.facebook.trim() || null,
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
      <FoodTruckPreview profile={profile} photoUri={shownPhoto} />

      <FormSection title="Identité" first>
        <TextField
          label="Nom commercial"
          placeholder="Le Camion Gourmand"
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
        <Hint>Les visiteurs filtrent la carte sur ce type.</Hint>
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
        <Hint>{profile.description.length} / 2000</Hint>
      </FormSection>

      <FormSection title="Apparence">
        <ThemedText type="smallBold">Photo</ThemedText>
        <View style={[styles.photoFrame, { borderColor: theme.border, backgroundColor: theme.sand }]}>
          {shownPhoto ? (
            <Image
              source={{ uri: shownPhoto }}
              style={styles.photo}
              contentFit="cover"
              accessibilityLabel="Photo du food truck"
            />
          ) : (
            <View style={styles.photoEmpty} importantForAccessibility="no">
              <ThemedText style={styles.photoEmptyIcon}>📷</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Aucune photo
              </ThemedText>
            </View>
          )}
        </View>
        <View style={styles.row}>
          <View style={styles.rowItem}>
            <Button
              label={shownPhoto ? 'Changer la photo' : 'Ajouter une photo'}
              variant="secondary"
              icon={{ ios: 'photo', android: 'image', web: 'image' }}
              onPress={() => void pickPhoto()}
            />
          </View>
          {shownPhoto ? (
            <Button label="Retirer" variant="ghost" onPress={() => onPhotoChange({ kind: 'remove' })} />
          ) : null}
        </View>

        <View style={[styles.markerPanel, { borderColor: theme.border, backgroundColor: theme.background }]}>
          <View style={styles.markerHeader}>
            <View style={[styles.preview, { backgroundColor: marker.color }]} importantForAccessibility="no">
              <ThemedText style={styles.previewEmoji}>{marker.emoji}</ThemedText>
            </View>
            <View style={styles.markerHeaderText}>
              <ThemedText type="smallBold">Pastille sur la carte</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {profile.markerEmoji ? 'Emoji et couleur choisis' : 'Emoji du type de cuisine par défaut'}
              </ThemedText>
            </View>
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Emoji
          </ThemedText>
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
          <ThemedText type="small" themeColor="textSecondary">
            Couleur
          </ThemedText>
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
      </FormSection>

      <FormSection title="Contact et réseaux">
        <TextField
          label="Contact ou lien utile"
          placeholder="Téléphone, site, réseau social…"
          value={profile.contact}
          onChangeText={(v) => onChange({ ...profile, contact: v })}
          autoCapitalize="none"
          maxLength={512}
        />
        <TextField
          label="Instagram (facultatif)"
          placeholder="@moncamion"
          value={profile.instagram}
          onChangeText={(v) => onChange({ ...profile, instagram: v })}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          maxLength={300}
        />
        <TextField
          label="Facebook (facultatif)"
          placeholder="https://www.facebook.com/moncamion"
          value={profile.facebook}
          onChangeText={(v) => onChange({ ...profile, facebook: v })}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          maxLength={300}
        />
        <Hint>Les liens s’affichent sur la fiche du food truck.</Hint>
      </FormSection>
    </>
  );
}

/** Aperçu de la fiche, mis à jour pendant la saisie : photo (ou couleur de la pastille), nom, cuisine, description. */
export function FoodTruckPreview({ profile, photoUri: uri }: { profile: ProfileForm; photoUri: string | null }) {
  const theme = useTheme();
  const marker = markerOf(profile);
  const meta = [cuisineLabel(profile.cuisineType || null), profile.category].filter(Boolean).join(' · ');
  return (
    <View
      style={[styles.previewCard, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}
      accessibilityLabel="Aperçu de la fiche">
      <View style={[styles.previewCover, { backgroundColor: marker.color }]}>
        {uri ? (
          <Image source={{ uri }} style={styles.photo} contentFit="cover" accessibilityElementsHidden />
        ) : (
          <ThemedText style={styles.previewCoverEmoji}>{marker.emoji}</ThemedText>
        )}
        <View style={[styles.preview, styles.previewMarker, { backgroundColor: marker.color }]}>
          <ThemedText style={styles.previewEmoji}>{marker.emoji}</ThemedText>
        </View>
      </View>
      <View style={styles.previewBody}>
        <ThemedText type="eyebrow" themeColor="textSecondary">
          Aperçu de votre fiche
        </ThemedText>
        <ThemedText type="heading" numberOfLines={1}>
          {profile.name || 'Nom de votre food truck'}
        </ThemedText>
        <ThemedText type="smallBold" themeColor="accent" numberOfLines={1}>
          {meta || 'Type de cuisine · spécialité'}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={3}>
          {profile.description || 'Votre description apparaîtra ici.'}
        </ThemedText>
      </View>
    </View>
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
  rowItem: {
    flex: 1,
  },
  photoFrame: {
    height: 170,
    overflow: 'hidden',
    borderRadius: Radius.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
  },
  photo: {
    width: '100%',
    height: '100%',
  },
  photoEmpty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
  },
  photoEmptyIcon: {
    fontSize: 30,
    lineHeight: 38,
  },
  markerPanel: {
    gap: Spacing.two,
    padding: Spacing.three,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.md,
  },
  markerHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.one,
    marginBottom: Spacing.one,
  },
  markerHeaderText: {
    flex: 1,
  },
  previewCard: {
    overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.lg,
  },
  previewCover: {
    height: 120,
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewCoverEmoji: {
    fontSize: 44,
    lineHeight: 54,
  },
  previewMarker: {
    position: 'absolute',
    left: Spacing.three,
    bottom: -18,
  },
  previewBody: {
    gap: Spacing.half,
    padding: Spacing.three,
    paddingTop: Spacing.four,
  },
  preview: {
    width: 36,
    height: 36,
    borderRadius: 18,
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
