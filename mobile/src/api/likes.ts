import { request } from './http';
import type { CallOptions } from './activities';
import type { Activity } from './types';

/** Réponse de `POST` / `DELETE /activities/{id}/like` : nouvel état confirmé par le backend. */
export interface LikeStatus {
  liked: boolean;
}

/** `GET /users/me/likes` — activités aimées par l'utilisateur connecté (JWT requis). */
export function listLikedActivities(options: CallOptions = {}): Promise<Activity[]> {
  return request<Activity[]>('/api/v1/users/me/likes', { authenticated: true, signal: options.signal });
}

/** `POST /activities/{id}/like` — ajoute l'activité aux favoris (idempotent ; 404 si elle n'existe pas). */
export function likeActivity(id: number, options: CallOptions = {}): Promise<LikeStatus> {
  return request<LikeStatus>(`/api/v1/activities/${id}/like`, {
    method: 'POST',
    authenticated: true,
    signal: options.signal,
  });
}

/** `DELETE /activities/{id}/like` — retire l'activité des favoris (idempotent). */
export function unlikeActivity(id: number, options: CallOptions = {}): Promise<LikeStatus> {
  return request<LikeStatus>(`/api/v1/activities/${id}/like`, {
    method: 'DELETE',
    authenticated: true,
    signal: options.signal,
  });
}
