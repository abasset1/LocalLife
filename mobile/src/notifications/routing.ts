/**
 * Écran à ouvrir au toucher d'une notification d'alerte (données envoyées
 * par `ActivityAlertNotifier` côté backend) : la fiche de l'activité ou du
 * food truck s'il n'y a qu'une nouveauté, sinon la carte. Fonction pure,
 * testable sans React Native.
 */
export function routeForNotification(data: unknown): string | null {
  if (data === null || typeof data !== 'object') return null;
  const { type, activityId, foodTruckId } = data as {
    type?: unknown;
    activityId?: unknown;
    foodTruckId?: unknown;
  };
  if (type !== 'activity-alert') return null;
  const activity = validId(activityId);
  if (activity) return `/activite/${activity}`;
  const foodTruck = validId(foodTruckId);
  if (foodTruck) return `/foodtruck/${foodTruck}`;
  return '/';
}

function validId(raw: unknown): number | null {
  const id = typeof raw === 'string' ? Number(raw) : raw;
  return typeof id === 'number' && Number.isInteger(id) && id > 0 ? id : null;
}
