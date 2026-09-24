import { useCallback, useEffect, useState } from 'react';

import { ApiError, HttpError, RequestAbortedError, getActivity } from '@/api';
import type { ActivityDetailOrSummary } from '@/api';

export type ActivityState =
  | { status: 'loading' }
  | { status: 'success'; activity: ActivityDetailOrSummary }
  /** Inexistante, identifiant invalide ou non publiée : rien à réessayer. */
  | { status: 'unavailable' }
  | { status: 'error'; message: string };

/**
 * Charge la fiche d'une activité (`GET /activities/{id}`, LL-MOB-0008).
 *
 * Une activité qui n'existe pas (404), dont l'identifiant est invalide
 * (400), ou qui n'est pas publiée (`status` ≠ `PUBLISHED`, quand la fiche
 * détaillée l'indique) est « indisponible » ; les autres échecs (réseau,
 * délai, 5xx) sont des erreurs réessayables.
 */
export function useActivity(id: string | undefined) {
  const [state, setState] = useState<ActivityState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!id) {
      setState({ status: 'unavailable' });
      return;
    }
    const controller = new AbortController();
    setState({ status: 'loading' });

    getActivity(id, { signal: controller.signal })
      .then((activity) => {
        if ('status' in activity && activity.status !== 'PUBLISHED') {
          setState({ status: 'unavailable' });
        } else {
          setState({ status: 'success', activity });
        }
      })
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        if (e instanceof HttpError && (e.isNotFound || e.isBadRequest)) {
          setState({ status: 'unavailable' });
          return;
        }
        setState({
          status: 'error',
          message: e instanceof ApiError ? e.message : "Impossible de charger l'activité.",
        });
      });

    return () => controller.abort();
  }, [id, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { state, retry };
}
