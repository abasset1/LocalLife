import type { Place } from '@/api';

/** Lieux triés pour la liste : par commune puis par nom (ordre français) ; les lieux sans commune en dernier. */
export function sortPlacesForList(places: Place[]): Place[] {
  return [...places].sort((a, b) => {
    if (a.city !== b.city) {
      if (a.city === null) return 1;
      if (b.city === null) return -1;
      const byCity = a.city.localeCompare(b.city, 'fr');
      if (byCity !== 0) return byCity;
    }
    return a.name.localeCompare(b.name, 'fr');
  });
}
