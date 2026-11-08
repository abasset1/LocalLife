import { useCallback, useEffect, useState } from 'react';

import { RequestAbortedError, searchPlacesWithinBounds, type Place } from '@/api';
import { regionForRadius, regionToBounds } from '@/features/map/geo';

export type PlaceListState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; places: Place[] }
  | { status: 'error'; message: string };

/**
 * Lieux publiés autour du lieu de recherche, pour la liste (univers « Lieux » et « Tous ») : la zone carrée du
 * rayon est demandée au serveur (`GET /places/within-bounds`), l'écran garde ensuite ceux qui sont dans le cercle.
 */
export function usePlaceList(
  origin: { latitude: number; longitude: number } | null,
  radiusKm: number,
  enabled: boolean,
) {
  const [state, setState] = useState<PlaceListState>({ status: 'idle' });
  const [refreshing, setRefreshing] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const latitude = origin?.latitude ?? null;
  const longitude = origin?.longitude ?? null;

  useEffect(() => {
    if (!enabled || latitude === null || longitude === null) return;
    const bounds = regionToBounds(regionForRadius(latitude, longitude, radiusKm));
    if (!bounds) return;
    const controller = new AbortController();
    setState((current) => (current.status === 'success' ? current : { status: 'loading' }));
    searchPlacesWithinBounds(bounds, { signal: controller.signal })
      .then((places) => setState({ status: 'success', places }))
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        setState({ status: 'error', message: 'Impossible de charger les lieux.' });
      })
      .finally(() => {
        if (!controller.signal.aborted) setRefreshing(false);
      });
    return () => controller.abort();
  }, [enabled, latitude, longitude, radiusKm, attempt]);

  const refresh = useCallback(() => {
    setRefreshing(true);
    setAttempt((n) => n + 1);
  }, []);

  return { state: enabled && latitude !== null ? state : ({ status: 'idle' } as PlaceListState), refreshing, refresh };
}
