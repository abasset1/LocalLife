import { request } from './http';
import type { CallOptions } from './activities';
import type { Category } from './types';

/** `GET /categories` — endpoint public, libellés en français. */
export function listCategories(options: CallOptions = {}): Promise<Category[]> {
  return request<Category[]>('/api/v1/categories', { query: { locale: 'fr-FR' }, signal: options.signal });
}
