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
export { createAlert, deleteAlert, listAlerts, updateAlertChannels } from './alerts';
export { listCategories } from './categories';
export { listFoodTrucks } from './foodTrucks';
export { checkUsernameAvailability, login, register } from './auth';
export { getPreferences, lookupCity, updatePreferences } from './preferences';
export { registerPushToken, unregisterPushToken } from './pushTokens';
