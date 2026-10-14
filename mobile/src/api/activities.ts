import { request } from './http';
import type {
  Activity,
  ActivityDetailOrSummary,
  ActivitySummary,
  Contribution,
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

/**
 * `GET /activities/at-place/{placeId}` — activités à venir d'un lieu permanent publié et des lieux d'événement
 * reliés (au plus 50), même format que la carte. 404 si le lieu n'est pas publié.
 */
export function listActivitiesAtPlace(placeId: number, options: CallOptions = {}): Promise<Activity[]> {
  return request<Activity[]>(`${BASE}/at-place/${placeId}`, { signal: options.signal });
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
    // Le niveau de détail dépend des droits de l'appelant (`PremiumAccessPolicy`).
    authenticated: 'optional',
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

/** `GET /activities/mine` — contributions de l'utilisateur, tous statuts confondus (JWT requis). */
export function listMyContributions(options: CallOptions = {}): Promise<Contribution[]> {
  return request<Contribution[]>(`${BASE}/mine`, { authenticated: true, signal: options.signal });
}

/** `GET /activities/{id}/contribution` — contribution à modifier (créateur ou administrateur). */
export function getContribution(id: number | string, options: CallOptions = {}): Promise<Contribution> {
  return request<Contribution>(`${BASE}/${encodeURIComponent(String(id))}/contribution`, {
    authenticated: true,
    signal: options.signal,
  });
}

/**
 * `PUT /activities/{id}` — modifie une contribution (même corps que la création). Faite par le
 * contributeur, elle repasse l'activité en attente de validation (`PENDING`).
 */
export function updateActivity(
  id: number,
  payload: CreateActivityRequest,
  options: CallOptions = {},
): Promise<CreatedActivity> {
  return request<CreatedActivity>(`${BASE}/${id}`, {
    method: 'PUT',
    body: payload,
    authenticated: true,
    signal: options.signal,
  });
}

/** `DELETE /activities/{id}` — suppression définitive (créateur ou administrateur). */
export function deleteActivity(id: number, options: CallOptions = {}): Promise<void> {
  return request<void>(`${BASE}/${id}`, { method: 'DELETE', authenticated: true, signal: options.signal });
}
