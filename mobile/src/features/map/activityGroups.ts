import type { Activity } from '@/api';

/**
 * Regroupements d'activités de la carte, comme le site web (page Explorer) : popup qui fait défiler les activités
 * d'une pastille ou d'un marqueur, zoom réservé aux grosses pastilles, liste de la zone affichée.
 */

/**
 * Au-delà de ce nombre d'activités, une pastille dont les activités sont à des endroits différents zoome sur son
 * groupe au lieu d'ouvrir la popup : on ne fait pas défiler des dizaines d'activités une à une.
 */
export const CLUSTER_POPUP_MAX_ACTIVITIES = 10;

type Point = { latitude: number; longitude: number };

/** Positions arrondies à environ 100 m : en dessous, deux activités sont considérées au même endroit. */
export function placeKey(point: Point): string {
  return `${point.latitude.toFixed(3)},${point.longitude.toFixed(3)}`;
}

/** De la plus proche dans le temps à la plus lointaine ; les activités sans date en dernier. */
export function compareByStartDate(a: Activity, b: Activity): number {
  if (!a.startDate || !b.startDate) return a.startDate ? -1 : b.startDate ? 1 : 0;
  return a.startDate.localeCompare(b.startDate);
}

export function sortByStartDate(activities: readonly Activity[]): Activity[] {
  return [...activities].sort(compareByStartDate);
}

/** Marqueur seul touché : cette activité d'abord, puis les autres au même endroit (à 100 m près), par date. */
export function activitiesAtSamePlace(activity: Activity, all: readonly Activity[]): Activity[] {
  const key = placeKey(activity);
  return [activity, ...sortByStartDate(all.filter((other) => other.id !== activity.id && placeKey(other) === key))];
}

/**
 * Pastille touchée : `zoom` pour plus de {@link CLUSTER_POPUP_MAX_ACTIVITIES} activités à des endroits différents,
 * quand la carte peut encore les séparer ; sinon `browse` (popup qui les fait défiler), quel que soit leur nombre.
 */
export function clusterTapAction(activities: readonly Activity[], canZoom: boolean): 'zoom' | 'browse' {
  const spreadOut = new Set(activities.map(placeKey)).size > 1;
  return activities.length > CLUSTER_POPUP_MAX_ACTIVITIES && spreadOut && canZoom ? 'zoom' : 'browse';
}

/** Activité affichée après un pas de `step` dans le groupe, en boucle (« › » sur la dernière revient à la première). */
export function stepInGroup(group: readonly Activity[], current: Activity, step: number): Activity {
  const total = group.length;
  if (total === 0) return current;
  const index = Math.max(0, group.findIndex((activity) => activity.id === current.id));
  return group[(((index + step) % total) + total) % total];
}

/** Position « 2 / 4 » de l'activité dans son groupe. */
export function positionInGroup(group: readonly Activity[], current: Activity): string {
  const index = Math.max(0, group.findIndex((activity) => activity.id === current.id));
  return `${index + 1} / ${group.length}`;
}

/** « 37 activités dans cette zone » (en tête de la liste de la zone). */
export function zoneCountLabel(count: number): string {
  if (count === 0) return 'Aucune activité dans cette zone';
  return `${count} activité${count > 1 ? 's' : ''} dans cette zone`;
}
