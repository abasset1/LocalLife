import type { Place } from '@/api';

import { sortPlacesForList } from '../placeList';

function place(id: number, name: string, city: string | null): Place {
  return {
    id, name, city, placeType: 'Museum', address: null, postalCode: null, latitude: 43.95, longitude: 4.8,
    description: null, website: null, coordinatePrecision: 'UNKNOWN',
  };
}

describe('sortPlacesForList', () => {
  it('trie par commune puis par nom, les lieux sans commune en dernier', () => {
    const sorted = sortPlacesForList([
      place(1, 'Palais des Papes', 'Avignon'),
      place(2, 'Sans commune', null),
      place(3, 'Musée de la Lavande', 'Coustellet'),
      place(4, 'Écomusée', 'Avignon'),
    ]);
    expect(sorted.map((p) => p.id)).toEqual([4, 1, 3, 2]);
  });
});
