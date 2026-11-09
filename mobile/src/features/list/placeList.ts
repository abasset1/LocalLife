import type { Activity, FoodTruck, Place } from '@/api';

import { distanceKm, normalize } from './filters';

/**
 * Lieux et tri par distance de la liste (issue #226), sans dépendance à React Native pour rester testables.
 */

export interface NearPlace {
  place: Place;
  km: number;
}

/** Lieux à moins de `radiusKm` du lieu de recherche, filtrés par le texte saisi, du plus proche au plus loin. */
export function placesNear(
  places: readonly Place[],
  origin: { latitude: number; longitude: number },
  radiusKm: number,
  query: string,
): NearPlace[] {
  const words = normalize(query).split(/\s+/).filter(Boolean);
  return places
    .map((place) => ({ place, km: distanceKm(origin, place) }))
    .filter(({ km }) => km <= radiusKm)
    .filter(({ place }) => {
      if (words.length === 0) return true;
      const haystack = normalize([place.name, place.city, place.description].filter(Boolean).join(' '));
      return words.every((word) => haystack.includes(word));
    })
    .sort((a, b) => a.km - b.km);
}

export type ListItem =
  | { kind: 'activity'; activity: Activity; km: number }
  | { kind: 'foodTruck'; foodTruck: FoodTruck; km: number }
  | { kind: 'place'; place: Place; km: number };

/** Éléments de la liste, du plus proche au plus loin (ordre stable à distance égale), quels que soient leurs types. */
export function mergeByDistance(...groups: readonly ListItem[][]): ListItem[] {
  return groups.flat().sort((a, b) => a.km - b.km);
}

/** Clé unique d'un élément de la liste. */
export function listItemKey(item: ListItem): string {
  if (item.kind === 'activity') return `a-${item.activity.id}`;
  if (item.kind === 'foodTruck') return `t-${item.foodTruck.presenceId}`;
  return `p-${item.place.id}`;
}

/** Phrase sous les filtres : « 12 activités à moins de 20 km », « 1 lieu… », « 3 présences aujourd’hui… ». */
export function listSummary(
  layer: 'activities' | 'places' | 'foodTrucks' | 'night',
  count: number,
  radiusKm: number,
): string {
  const plural = count > 1 ? 's' : '';
  const within = `à moins de ${radiusKm} km`;
  if (layer === 'foodTrucks') {
    return count === 0 ? 'Aucun food truck présent aujourd’hui' : `${count} présence${plural} aujourd’hui ${within}`;
  }
  if (count === 0) return 'Aucun résultat';
  if (layer === 'places') return `${count} lieu${count > 1 ? 'x' : ''} ${within}`;
  if (layer === 'night') return `${count} sortie${plural} de nuit ${within}`;
  return `${count} activité${plural} ${within}`;
}
