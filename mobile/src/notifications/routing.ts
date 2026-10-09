/**
 * Écran à ouvrir au toucher d'une notification d'alerte (données envoyées
 * par `ActivityAlertNotifier` côté backend) : la fiche de l'activité s'il
 * n'y en a qu'une, sinon la carte. Fonction pure, testable sans React Native.
 */
export function routeForNotification(data: unknown): string | null {
  if (data === null || typeof data !== 'object') return null;
  const { type, activityId } = data as { type?: unknown; activityId?: unknown };
  if (type !== 'activity-alert') return null;
  const id = typeof activityId === 'string' ? Number(activityId) : activityId;
  if (typeof id === 'number' && Number.isInteger(id) && id > 0) return `/activite/${id}`;
  return '/';
}
