import { request } from './http';
import type { CallOptions } from './activities';
import type { CitySuggestion } from './types';

/**
 * `GET /geocoding/city-suggestions?q=…` — endpoint public (sans compte) : communes proposées pendant la
 * saisie, les plus peuplées d'abord. Liste vide sous 2 caractères ; 5 chiffres sont lus comme un code
 * postal. 503 si le service des communes est injoignable. Même source que la recherche du site web.
 */
export function suggestCities(query: string, options: CallOptions = {}): Promise<CitySuggestion[]> {
  return request<CitySuggestion[]>('/api/v1/geocoding/city-suggestions', {
    query: { q: query },
    signal: options.signal,
  });
}
