import { useCallback, useEffect, useState } from 'react';

import { ApiError, HttpError, RequestAbortedError, getFoodTruck } from '@/api';
import type { FoodTruckDetail } from '@/api';

export type FoodTruckState =
  | { status: 'loading' }
  | { status: 'success'; foodTruck: FoodTruckDetail }
  /** Inconnu ou non publié (404) : rien à réessayer. */
  | { status: 'unavailable' }
  | { status: 'error'; message: string };

/**
 * Charge un food truck (LL-MOB-0009) et son planning (`GET /foodtrucks/{id}`,
 * ADR-0013). Le backend ne renvoie qu'un food truck publié : un autre répond
 * 404 et n'est jamais affiché. La fiche ne passe plus par la liste du jour, qui
 * ne contient que les food trucks présents aujourd'hui.
 */
export function useFoodTruck(id: string | undefined) {
  const [state, setState] = useState<FoodTruckState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!id) {
      setState({ status: 'unavailable' });
      return;
    }
    const controller = new AbortController();
    setState({ status: 'loading' });

    getFoodTruck(id, { signal: controller.signal })
      .then((foodTruck) => setState({ status: 'success', foodTruck }))
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        if (e instanceof HttpError && e.status === 404) {
          setState({ status: 'unavailable' });
          return;
        }
        setState({
          status: 'error',
          message: e instanceof ApiError ? e.message : 'Impossible de charger le food truck.',
        });
      });

    return () => controller.abort();
  }, [id, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { state, retry };
}
