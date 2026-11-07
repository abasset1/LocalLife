import { request } from './http';
import type { CallOptions } from './activities';

/**
 * Fonctions ouvertes pour l'appelant (`GET /api/v1/features`, lancement progressif du 05/11/2026) : lieux, food
 * trucks, offres Premium et Pro. Le JWT est joint s'il y en a un : l'équipe Neiri et les administrateurs voient
 * tout, même ce qui est encore fermé au public.
 */
export interface LaunchFeaturesResponse {
  places: boolean;
  foodTrucks: boolean;
  premium: boolean;
  pro: boolean;
}

export function getLaunchFeatures(options: CallOptions = {}): Promise<LaunchFeaturesResponse> {
  return request<LaunchFeaturesResponse>('/api/v1/features', { authenticated: 'optional', signal: options.signal });
}
