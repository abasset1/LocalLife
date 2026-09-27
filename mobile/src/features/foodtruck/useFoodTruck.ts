import { useCallback, useEffect, useState } from 'react';

import { ApiError, RequestAbortedError, listFoodTrucks } from '@/api';
import type { FoodTruck } from '@/api';

export type FoodTruckState =
  | { status: 'loading' }
  | { status: 'success'; foodTruck: FoodTruck }
  /** Absent de la liste des food trucks publiés : rien à réessayer. */
  | { status: 'unavailable' }
  | { status: 'error'; message: string };

/**
 * Charge un food truck (LL-MOB-0009). L'API n'expose que la liste des food
 * trucks publiés (`GET /foodtrucks`, pas de lecture par identifiant) : la
 * fiche est retrouvée dans cette liste, ce qui garantit aussi qu'un food
 * truck non publié n'est jamais affiché.
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

    listFoodTrucks({ signal: controller.signal })
      .then((trucks) => {
        const foodTruck = trucks.find((t) => String(t.id) === id);
        setState(foodTruck ? { status: 'success', foodTruck } : { status: 'unavailable' });
      })
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
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
