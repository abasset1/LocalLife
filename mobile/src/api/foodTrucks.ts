import { request } from './http';
import type { CallOptions } from './activities';
import type { FoodTruck } from './types';

/** `GET /foodtrucks` — food trucks publiés, endpoint public. */
export function listFoodTrucks(options: CallOptions = {}): Promise<FoodTruck[]> {
  return request<FoodTruck[]>('/api/v1/foodtrucks', { signal: options.signal });
}
