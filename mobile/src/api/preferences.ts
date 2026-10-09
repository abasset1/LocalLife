import { request } from './http';
import type { CallOptions } from './activities';
import type { CityLookup, UpdatePreferencesRequest, UserPreferences } from './types';

/**
 * Paramètres de recherche par défaut de l'utilisateur connecté (JWT requis),
 * partagés avec le site web.
 */
const BASE = '/api/v1/users/me/preferences';

export function getPreferences(options: CallOptions = {}): Promise<UserPreferences> {
  return request<UserPreferences>(BASE, { authenticated: true, signal: options.signal });
}

/** Remplace l'ensemble des paramètres. 400 si la ville n'existe pas. */
export function updatePreferences(
  payload: UpdatePreferencesRequest,
  options: CallOptions = {},
): Promise<UserPreferences> {
  return request<UserPreferences>(BASE, { method: 'PUT', body: payload, authenticated: true, signal: options.signal });
}

/** Vérifie qu'une ville existe, par son nom ou par une position. 400 si introuvable. */
export function lookupCity(
  params: { name: string } | { latitude: number; longitude: number },
  options: CallOptions = {},
): Promise<CityLookup> {
  return request<CityLookup>(`${BASE}/city`, { query: params, authenticated: true, signal: options.signal });
}
