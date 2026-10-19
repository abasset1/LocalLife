import { useCallback, useEffect, useState } from 'react';

import { ApiError, RequestAbortedError, listFoodTrucks } from '@/api';
import type { FoodTruck } from '@/api';

export type FoodTruckListState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; foodTrucks: FoodTruck[] }
  | { status: 'error'; message: string };

/**
 * Food trucks présents aujourd'hui (`GET /foodtrucks`, ADR-0013), pour l'onglet « Food trucks » de la liste.
 * Chargés seulement quand l'onglet est ouvert (`enabled`) ; la distance et le filtre sont appliqués par
 * l'écran. `refresh` relance la requête (tirer pour actualiser, « Réessayer »).
 */
export function useFoodTruckList(enabled: boolean) {
  const [state, setState] = useState<FoodTruckListState>({ status: 'idle' });
  const [refreshing, setRefreshing] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setState((current) => (current.status === 'success' ? current : { status: 'loading' }));
    listFoodTrucks({ signal: controller.signal })
      .then((foodTrucks) => setState({ status: 'success', foodTrucks }))
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        setState({
          status: 'error',
          message: e instanceof ApiError ? e.message : 'Impossible de charger les food trucks.',
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setRefreshing(false);
      });
    return () => controller.abort();
  }, [enabled, attempt]);

  const refresh = useCallback(() => {
    setRefreshing(true);
    setAttempt((n) => n + 1);
  }, []);

  return { state, refreshing, refresh };
}
