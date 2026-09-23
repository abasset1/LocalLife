import { request } from './http';
import type { CallOptions } from './activities';
import type { Category } from './types';

/** `GET /categories` — endpoint public. */
export function listCategories(options: CallOptions = {}): Promise<Category[]> {
  return request<Category[]>('/api/v1/categories', { signal: options.signal });
}
