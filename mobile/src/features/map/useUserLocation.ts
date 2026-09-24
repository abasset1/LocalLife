import * as Location from 'expo-location';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Linking } from 'react-native';

/** Délai maximal accordé à l'obtention d'une position. */
export const LOCATION_TIMEOUT_MS = 15_000;

export type LocationStatus =
  | 'idle' // aucune demande en cours
  | 'locating' // permission ou position en cours d'obtention
  | 'success'
  | 'denied' // permission refusée
  | 'unavailable' // service de localisation désactivé sur l'appareil
  | 'error'; // échec ou délai dépassé

export interface UserPosition {
  latitude: number;
  longitude: number;
}

/** Message affiché à l'utilisateur pour chaque échec (aucun jargon technique). */
export const LOCATION_MESSAGES: Record<'denied' | 'unavailable' | 'error', string> = {
  denied:
    'La localisation est refusée. Vous pouvez continuer à explorer la carte, ou l’autoriser dans les réglages.',
  unavailable:
    'La localisation est désactivée sur cet appareil. Activez-la pour centrer la carte sur votre position.',
  error: 'Votre position n’a pas pu être déterminée. Réessayez dans un instant.',
};

class TimeoutReached extends Error {}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  let timer: ReturnType<typeof setTimeout>;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new TimeoutReached()), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Géolocalisation de l'utilisateur (LL-MOB-0006).
 *
 * - La permission n'est demandée qu'à l'appel de `locate()`, c'est-à-dire
 *   lorsque l'utilisateur touche le bouton « Ma position » — jamais au
 *   démarrage de l'application.
 * - Position « au premier plan » uniquement, obtenue à la demande (pas de
 *   suivi continu).
 * - La position n'est ni stockée ni envoyée telle quelle au serveur : elle
 *   sert uniquement à recentrer la carte, dont la zone visible alimente
 *   ensuite la recherche `within-bounds` habituelle.
 * - Un refus ou une erreur n'a aucun effet bloquant : la carte reste
 *   utilisable, seul le statut change.
 */
export function useUserLocation() {
  const [status, setStatus] = useState<LocationStatus>('idle');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const update = useCallback((next: LocationStatus) => {
    if (mounted.current) setStatus(next);
  }, []);

  /** Retourne la position, ou `null` si elle n'a pas pu être obtenue (le statut dit pourquoi). */
  const locate = useCallback(async (): Promise<UserPosition | null> => {
    update('locating');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (mounted.current) setCanAskAgain(permission.canAskAgain);
      if (permission.status !== Location.PermissionStatus.GRANTED) {
        update('denied');
        return null;
      }

      if (!(await Location.hasServicesEnabledAsync())) {
        update('unavailable');
        return null;
      }

      const { coords } = await withTimeout(
        Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
        LOCATION_TIMEOUT_MS,
      );
      update('success');
      return { latitude: coords.latitude, longitude: coords.longitude };
    } catch {
      update('error');
      return null;
    }
  }, [update]);

  const dismiss = useCallback(() => update('idle'), [update]);

  const openSettings = useCallback(() => {
    Linking.openSettings().catch(() => undefined);
  }, []);

  return { status, canAskAgain, locate, dismiss, openSettings };
}
