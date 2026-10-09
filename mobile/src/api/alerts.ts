import { request } from './http';
import type { CallOptions } from './activities';
import type { ActivityAlert, CreateAlertRequest } from './types';

/** Alertes de l'utilisateur connecté (JWT requis), partagées avec le site web. */
const BASE = '/api/v1/users/me/alerts';

export function listAlerts(options: CallOptions = {}): Promise<ActivityAlert[]> {
  return request<ActivityAlert[]>(BASE, { authenticated: true, signal: options.signal });
}

/** 400 : aucune catégorie, ville introuvable, aucun canal, ou 10 alertes déjà créées. */
export function createAlert(payload: CreateAlertRequest, options: CallOptions = {}): Promise<ActivityAlert> {
  return request<ActivityAlert>(BASE, { method: 'POST', body: payload, authenticated: true, signal: options.signal });
}

/** Change les canaux (e-mail / notification) ; au moins un doit rester actif. */
export function updateAlertChannels(
  id: number,
  channels: { notifyEmail: boolean; notifyPush: boolean },
  options: CallOptions = {},
): Promise<ActivityAlert> {
  return request<ActivityAlert>(`${BASE}/${id}`, {
    method: 'PATCH',
    body: channels,
    authenticated: true,
    signal: options.signal,
  });
}

export function deleteAlert(id: number, options: CallOptions = {}): Promise<void> {
  return request<void>(`${BASE}/${id}`, { method: 'DELETE', authenticated: true, signal: options.signal });
}
