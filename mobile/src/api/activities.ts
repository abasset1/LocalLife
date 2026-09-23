import { request } from './http';
import type {
  Activity,
  ActivityDetailOrSummary,
  ActivitySummary,
  CreateActivityRequest,
  CreatedActivity,
  ListActivitiesParams,
  NearbySearchParams,
  WithinBoundsSearchParams,
} from './types';

/** Options communes : annulation de la requête par l'appelant. */
export interface CallOptions {
  signal?: AbortSignal;
}

const BASE = '/api/v1/activities';

/** `GET /activities` — liste allégée, endpoint public. */
export function listActivities(
  params: ListActivitiesParams = {},
  options: CallOptions = {},
): Promise<ActivitySummary[]> {
  return request<ActivitySummary[]>(BASE, {
    query: { city: params.city, date: params.date, sort: params.sort },
    signal: options.signal,
  });
}

/** `GET /activities/nearby` — activités publiées dans un rayon, triées par distance. */
export function searchNearby(
  params: NearbySearchParams,
  options: CallOptions = {},
): Promise<Activity[]> {
  return request<Activity[]>(`${BASE}/nearby`, {
    query: {
      latitude: params.latitude,
      longitude: params.longitude,
      radius: params.radiusKm,
      category: params.category,
      date: params.date,
      dateTo: params.dateTo,
    },
    signal: options.signal,
  });
}

/** `GET /activities/within-bounds` — activités publiées dans la zone visible de la carte. */
export function searchWithinBounds(
  params: WithinBoundsSearchParams,
  options: CallOptions = {},
): Promise<Activity[]> {
  return request<Activity[]>(`${BASE}/within-bounds`, {
    query: {
      swLatitude: params.swLatitude,
      swLongitude: params.swLongitude,
      neLatitude: params.neLatitude,
      neLongitude: params.neLongitude,
      category: params.category,
      date: params.date,
      dateTo: params.dateTo,
    },
    signal: options.signal,
  });
}

/**
 * `GET /activities/{id}` — fiche détaillée ou résumé (voir `isActivityDetail`).
 * Lève `HttpError` avec `isNotFound` si l'activité n'existe pas.
 */
export function getActivity(
  id: number | string,
  options: CallOptions = {},
): Promise<ActivityDetailOrSummary> {
  return request<ActivityDetailOrSummary>(`${BASE}/${encodeURIComponent(String(id))}`, {
    signal: options.signal,
  });
}

/**
 * `POST /activities` — contribution (JWT requis). Le backend géocode
 * l'adresse ; l'activité créée est au statut `PENDING`.
 */
export function createActivity(
  payload: CreateActivityRequest,
  options: CallOptions = {},
): Promise<CreatedActivity> {
  return request<CreatedActivity>(BASE, {
    method: 'POST',
    body: payload,
    authenticated: true,
    signal: options.signal,
  });
}
