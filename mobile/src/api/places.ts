import type { CallOptions } from './activities';
import { request } from './http';
import type { Place } from './types';

/** Zone de recherche des lieux : mêmes bornes que la recherche d'activités par zone. */
export interface PlacesWithinBoundsParams {
  swLatitude: number;
  swLongitude: number;
  neLatitude: number;
  neLongitude: number;
}

/** `GET /places/within-bounds` — lieux permanents publiés de la zone (au plus 500), endpoint public. */
export function searchPlacesWithinBounds(
  params: PlacesWithinBoundsParams,
  options: CallOptions = {},
): Promise<Place[]> {
  return request<Place[]>('/api/v1/places/within-bounds', {
    query: {
      minLat: params.swLatitude,
      maxLat: params.neLatitude,
      minLng: params.swLongitude,
      maxLng: params.neLongitude,
    },
    signal: options.signal,
  });
}
