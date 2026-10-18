import { useCallback, useEffect, useState } from 'react';

import { ApiError, RequestAbortedError, suggestCities } from '@/api';
import type { CitySuggestion } from '@/api';

import { CITY_SEARCH_DEBOUNCE_MS, isCityQuery } from './citySearch';

export type CitySuggestionsState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; suggestions: CitySuggestion[] }
  | { status: 'error'; message: string };

/**
 * Communes proposées pour la saisie en cours (`GET /geocoding/city-suggestions`, public), après une
 * courte pause de frappe. Contrairement à une liste simplement vide, une panne est signalée
 * (`error`) et `retry` relance la même recherche. Une réponse arrivée pour une saisie déjà modifiée
 * est ignorée.
 */
export function useCitySuggestions(query: string, enabled = true): CitySuggestionsState & { retry: () => void } {
  const value = query.trim();
  const active = enabled && isCityQuery(value);
  const [result, setResult] = useState<{ query: string; state: CitySuggestionsState } | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      suggestCities(value, { signal: controller.signal })
        .then((suggestions) => setResult({ query: value, state: { status: 'success', suggestions } }))
        .catch((e: unknown) => {
          if (e instanceof RequestAbortedError || controller.signal.aborted) return;
          setResult({
            query: value,
            state: {
              status: 'error',
              message:
                e instanceof ApiError ? e.message : 'La recherche de villes est indisponible pour le moment.',
            },
          });
        });
    }, CITY_SEARCH_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [value, active, attempt]);

  const retry = useCallback(() => {
    setResult(null);
    setAttempt((n) => n + 1);
  }, []);

  if (!active) return { status: 'idle', retry };
  // Jamais de communes issues d'une saisie antérieure, même pendant la pause de frappe.
  if (!result || result.query !== value) return { status: 'loading', retry };
  return { ...result.state, retry };
}
