/**
 * Point d'entrée du client API LocalLife (LL-MOB-0003).
 * Les écrans importent uniquement depuis `@/api`.
 */
export { configureApiClient } from './http';
export type { ApiClientConfig } from './http';

export * from './errors';
export * from './types';

export {
  createActivity,
  deleteActivity,
  getActivity,
  getContribution,
  listActivities,
  listActivitiesAtPlace,
  listMyContributions,
  searchNearby,
  searchWithinBounds,
  updateActivity,
} from './activities';
export type { CallOptions } from './activities';
export { createAlert, deleteAlert, listAlerts, updateAlertChannels } from './alerts';
export { listCategories } from './categories';
export { listFoodTrucks } from './foodTrucks';
export { searchPlacesWithinBounds } from './places';
export type { PlacesWithinBoundsParams } from './places';
export { checkUsernameAvailability, forgotPassword, login, register, resendVerification } from './auth';
export type { MessageResponse } from './auth';
export { likeActivity, listLikedActivities, unlikeActivity } from './likes';
export type { LikeStatus } from './likes';
export { getPreferences, lookupCity, updatePreferences } from './preferences';
export { registerPushToken, unregisterPushToken } from './pushTokens';
