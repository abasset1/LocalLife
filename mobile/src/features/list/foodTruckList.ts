import type { FoodTruck } from '@/api';

import { cuisineLabel } from '@/features/foodtruck/cuisines';

import { distanceKm, normalize } from './filters';

/** Food truck de la liste, avec sa distance au lieu de recherche. */
export interface NearbyFoodTruck {
  foodTruck: FoodTruck;
  distanceKm: number;
}

/**
 * Food trucks présents aujourd'hui (un élément par présence, ADR-0013) à moins de `radiusKm` du lieu de
 * recherche, triés par distance puis par nom. Le filtre texte porte sur le nom, la cuisine (spécialité et type), la
 * description et l'emplacement ; chaque mot saisi doit s'y trouver. `cuisine` : type de cuisine choisi, `null` = tous.
 */
export function foodTrucksNear(
  foodTrucks: readonly FoodTruck[],
  center: { latitude: number; longitude: number },
  radiusKm: number,
  query: string,
  cuisine: string | null = null,
): NearbyFoodTruck[] {
  const words = normalize(query).split(/\s+/).filter(Boolean);
  return foodTrucks
    .map((foodTruck) => ({ foodTruck, distanceKm: distanceKm(center, foodTruck) }))
    .filter(({ foodTruck, distanceKm: km }) => {
      if (km > radiusKm) return false;
      if (cuisine !== null && foodTruck.cuisineType !== cuisine) return false;
      if (words.length === 0) return true;
      const haystack = normalize(
        [
          foodTruck.name,
          foodTruck.category,
          cuisineLabel(foodTruck.cuisineType),
          foodTruck.description,
          foodTruck.spotName,
        ].filter(Boolean).join(' '),
      );
      return words.every((word) => haystack.includes(word));
    })
    .sort((a, b) => a.distanceKm - b.distanceKm || a.foodTruck.name.localeCompare(b.foodTruck.name, 'fr'));
}
