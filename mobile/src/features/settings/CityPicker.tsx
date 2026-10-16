import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiError, lookupCity } from '@/api';
import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { LOCATION_MESSAGES, useUserLocation } from '@/features/map/useUserLocation';
import { useTheme } from '@/hooks/use-theme';

export type CityStatus =
  | { kind: 'idle' }
  | { kind: 'checking' }
  | { kind: 'valid'; message: string | null }
  | { kind: 'invalid'; message: string };

export interface CityPickerState {
  city: string;
  /** Position de l'appareil ayant servi à choisir la ville ; `null` = ville saisie. */
  position: { latitude: number; longitude: number } | null;
  status: CityStatus;
  /** Ville déjà enregistrée : affichée comme valide sans nouvelle vérification. */
  setSavedCity: (city: string | null) => void;
  onChangeText: (value: string) => void;
  /** Vérifie la ville saisie (à la sortie du champ). */
  verify: () => Promise<void>;
  locateCity: () => Promise<void>;
  locating: boolean;
  /** Vrai tant qu'une vérification est en cours ou que la ville est introuvable. */
  blocksSubmit: boolean;
}

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Impossible de vérifier cette ville.';
}

/**
 * Choix d'une ville par saisie ou par géolocalisation (mêmes règles que le
 * site web) : le backend vérifie qu'elle existe et renvoie son nom officiel.
 * Avec la géolocalisation, seule la ville est retenue, jamais la position
 * exacte.
 */
export function useCityPicker(onEdit?: () => void): CityPickerState {
  const [city, setCity] = useState('');
  const [position, setPosition] = useState<{ latitude: number; longitude: number } | null>(null);
  const [status, setStatus] = useState<CityStatus>({ kind: 'idle' });
  const location = useUserLocation();

  const setSavedCity = useCallback((saved: string | null) => {
    setCity(saved ?? '');
    setPosition(null);
    setStatus(saved ? { kind: 'valid', message: null } : { kind: 'idle' });
  }, []);

  const onChangeText = useCallback(
    (value: string) => {
      setCity(value);
      setPosition(null);
      setStatus({ kind: 'idle' });
      onEdit?.();
    },
    [onEdit],
  );

  const verify = useCallback(async () => {
    if (status.kind !== 'idle' || city.trim() === '') return;
    setStatus({ kind: 'checking' });
    try {
      const found = await lookupCity({ name: city.trim() });
      setCity(found.city);
      setStatus({ kind: 'valid', message: `Ville trouvée : ${found.city}` });
    } catch (e) {
      setStatus({ kind: 'invalid', message: messageFor(e) });
    }
  }, [city, status.kind]);

  const { locate } = location;
  const locateCity = useCallback(async () => {
    const coordinates = await locate();
    if (!coordinates) return;
    setStatus({ kind: 'checking' });
    try {
      const found = await lookupCity(coordinates);
      setCity(found.city);
      setPosition(coordinates);
      setStatus({ kind: 'valid', message: `Ville détectée : ${found.city}` });
      onEdit?.();
    } catch (e) {
      setStatus({ kind: 'invalid', message: messageFor(e) });
    }
  }, [locate, onEdit]);

  const locationProblem =
    location.status === 'denied' || location.status === 'unavailable' || location.status === 'error'
      ? LOCATION_MESSAGES[location.status]
      : null;
  const effectiveStatus: CityStatus =
    locationProblem && status.kind !== 'valid' ? { kind: 'invalid', message: locationProblem } : status;

  return {
    city,
    position,
    status: effectiveStatus,
    setSavedCity,
    onChangeText,
    verify,
    locateCity,
    locating: location.status === 'locating',
    blocksSubmit: status.kind === 'checking' || status.kind === 'invalid',
  };
}

export function CityPicker({ label, picker }: { label: string; picker: CityPickerState }) {
  const theme = useTheme();
  const { status } = picker;
  return (
    <View style={styles.wrapper}>
      <TextField
        label={label}
        value={picker.city}
        onChangeText={picker.onChangeText}
        onBlur={() => void picker.verify()}
        onSubmitEditing={() => void picker.verify()}
        placeholder="Ex. : Lyon"
        autoCorrect={false}
        textContentType="addressCity"
        returnKeyType="done"
      />
      <Button
        label="Utiliser ma position"
        icon={{ ios: 'location.fill', android: 'my_location', web: 'my_location' }}
        variant="secondary"
        loading={picker.locating || status.kind === 'checking'}
        onPress={() => void picker.locateCity()}
      />
      <View accessibilityLiveRegion="polite">
        {status.kind === 'valid' && status.message && (
          <ThemedText type="small" style={{ color: theme.success }}>
            ✓ {status.message}
          </ThemedText>
        )}
        {status.kind === 'invalid' && (
          <ThemedText type="small" accessibilityRole="alert" style={{ color: theme.danger }}>
            {status.message}
          </ThemedText>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.two,
  },
});
