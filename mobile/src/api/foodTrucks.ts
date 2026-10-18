import { request } from './http';
import type { CallOptions } from './activities';
import type { FoodTruck, FoodTruckDetail } from './types';

/** `GET /foodtrucks` — food trucks présents aujourd'hui, un élément par présence (ADR-0013), endpoint public. */
export function listFoodTrucks(options: CallOptions = {}): Promise<FoodTruck[]> {
  return request<FoodTruck[]>('/api/v1/foodtrucks', { signal: options.signal });
}

/** `GET /foodtrucks/{id}` — fiche d'un food truck publié et son planning ; 404 sinon. */
export function getFoodTruck(id: string, options: CallOptions = {}): Promise<FoodTruckDetail> {
  return request<FoodTruckDetail>(`/api/v1/foodtrucks/${encodeURIComponent(id)}`, { signal: options.signal });
}
