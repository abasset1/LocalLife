/**
 * Point d'entrée du client API LocalLife (LL-MOB-0003).
 * Les écrans importent uniquement depuis `@/api`.
 */
export { configureApiClient } from './http';
export type { ApiClientConfig } from './http';

export * from './errors';
export * from './types';

export { createActivity, getActivity, listActivities, searchNearby, searchWithinBounds } from './activities';
export type { CallOptions } from './activities';
export { listCategories } from './categories';
export { listFoodTrucks } from './foodTrucks';
export { login, register } from './auth';
