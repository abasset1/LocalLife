import { useCallback, useEffect, useState } from 'react';

import { ApiError, RequestAbortedError, searchNearby } from '@/api';
import type { Activity } from '@/api';

import { periodRange, type Period } from './filters';

export type ActivityListState =
  /** Aucun lieu de recherche : rien n'est chargé. */
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; activities: Activity[] }
  | { status: 'error'; message: string };

const IDLE: ActivityListState = { status: 'idle' };

export interface ListSearch {
  latitude: number;
  longitude: number;
  /** Au plus 50 km (limite de `GET /activities/nearby`). */
  radiusKm: number;
  period: Period;
  /** Codes de catégories ; vide = toutes. */
  categoryCodes: readonly string[];
}

/**
 * Activités publiées autour d'un point (`GET /activities/nearby`, triées par distance), pour l'onglet
 * « Liste ». Rechargées à chaque changement de critère ; la requête précédente est annulée.
 * `refresh` relance la même recherche (tirer pour actualiser, « Réessayer »). Sans lieu de recherche
 * (`search` à `null`), aucune requête : jamais de recherche autour d'une ville supposée.
 */
export function useActivityList(search: ListSearch | null) {
  const [state, setState] = useState<ActivityListState>({ status: search ? 'loading' : 'idle' });
  const [refreshing, setRefreshing] = useState(false);
  const [attempt, setAttempt] = useState(0);

  const latitude = search?.latitude ?? null;
  const longitude = search?.longitude ?? null;
  const radiusKm = search?.radiusKm ?? 0;
  const period = search?.period ?? 'all';
  const categoryKey = search ? [...search.categoryCodes].sort().join(',') : '';

  useEffect(() => {
    if (latitude === null || longitude === null) return;
    const controller = new AbortController();
    setState((current) => (current.status === 'success' ? current : { status: 'loading' }));
    searchNearby(
      {
        latitude,
        longitude,
        radiusKm: Math.min(radiusKm, 50),
        category: categoryKey === '' ? undefined : categoryKey,
        ...periodRange(period),
      },
      { signal: controller.signal },
    )
      .then((activities) => setState({ status: 'success', activities }))
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        setState({
          status: 'error',
          message: e instanceof ApiError ? e.message : 'Impossible de charger les activités.',
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setRefreshing(false);
      });
    return () => controller.abort();
  }, [latitude, longitude, radiusKm, period, categoryKey, attempt]);

  const refresh = useCallback(() => {
    setRefreshing(true);
    setAttempt((n) => n + 1);
  }, []);

  if (latitude === null) return { state: IDLE, refreshing: false, refresh };
  return { state, refreshing, refresh };
}
