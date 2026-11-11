import { request } from './http';
import type { CallOptions } from './activities';

export type FollowTargetType = 'activity' | 'place' | 'foodtruck';

/** Activité, lieu ou food truck suivi (`GET /users/me/follows`), du plus récent au plus ancien. */
export interface FollowedItem {
  type: FollowTargetType;
  id: number;
  title: string | null;
  city: string | null;
  /** Statut de l'activité (`PUBLISHED`, `CANCELLED`…), du lieu ou du food truck (`PUBLISHED`, `ARCHIVED`…). */
  status: string | null;
  followedAt: string;
}

/** Réponse de `POST` / `DELETE …/follow` : nouvel état confirmé par le backend. */
export interface FollowStatus {
  following: boolean;
}

function followPath(type: FollowTargetType, id: number): string {
  const collection = { activity: 'activities', place: 'places', foodtruck: 'foodtrucks' }[type];
  return `/api/v1/${collection}/${id}/follow`;
}

/** `GET /users/me/follows` — activités, lieux et food trucks suivis par l'utilisateur connecté (JWT requis). */
export function listFollows(options: CallOptions = {}): Promise<FollowedItem[]> {
  return request<FollowedItem[]>('/api/v1/users/me/follows', { authenticated: true, signal: options.signal });
}

/**
 * `POST /activities/{id}/follow`, `/places/{id}/follow` ou `/foodtrucks/{id}/follow` — être prévenu des mises à jour
 * (idempotent). 403 pour un compte gratuit (réservé à Premium et Pro), 404 si la cible n'est pas visible.
 */
export function follow(type: FollowTargetType, id: number, options: CallOptions = {}): Promise<FollowStatus> {
  return request<FollowStatus>(followPath(type, id), { method: 'POST', authenticated: true, signal: options.signal });
}

/** `DELETE …/follow` — ne plus suivre (idempotent, quelle que soit l'offre). */
export function unfollow(type: FollowTargetType, id: number, options: CallOptions = {}): Promise<FollowStatus> {
  return request<FollowStatus>(followPath(type, id), {
    method: 'DELETE',
    authenticated: true,
    signal: options.signal,
  });
}
