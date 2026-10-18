import type { FoodTruck } from '@/api';

import { foodTrucksNear } from '../foodTruckList';

const AVIGNON = { latitude: 43.9493, longitude: 4.8055 };

function truck(overrides: Partial<FoodTruck>): FoodTruck {
  return {
    id: 1, name: 'Tacos Loco', description: null, latitude: 43.9493, longitude: 4.8055, category: 'Tacos',
    contact: null, status: 'PUBLISHED', presenceId: 1, spotName: 'Place Pie', spotKind: 'MARKET',
    daysOfWeek: null, startTime: null, endTime: null, validFrom: null, validUntil: null, placeId: null,
    ...overrides,
  };
}

describe('foodTrucksNear', () => {
  const near = truck({ presenceId: 1, name: 'Tacos Loco', latitude: 43.951 });
  const nearer = truck({ presenceId: 2, id: 2, name: 'Crêpes du Rhône', category: 'Crêpes', latitude: 43.9494 });
  const far = truck({ presenceId: 3, id: 3, name: 'Loin', latitude: 43.2965, longitude: 5.3698 });

  it('garde les présences dans le rayon, les plus proches d’abord', () => {
    expect(foodTrucksNear([near, far, nearer], AVIGNON, 20, '').map((t) => t.foodTruck.presenceId))
      .toEqual([2, 1]);
  });

  it('filtre sur le nom, la cuisine et l’emplacement, sans accents ni casse', () => {
    expect(foodTrucksNear([near, nearer], AVIGNON, 20, 'crepes').map((t) => t.foodTruck.presenceId)).toEqual([2]);
    expect(foodTrucksNear([near, nearer], AVIGNON, 20, 'place pie').map((t) => t.foodTruck.presenceId))
      .toEqual([2, 1]);
    expect(foodTrucksNear([near, nearer], AVIGNON, 20, 'burger')).toEqual([]);
  });

  it('montre deux fois un food truck présent à deux endroits proches', () => {
    const second = truck({ presenceId: 4, latitude: 43.95, spotName: 'Parking' });
    expect(foodTrucksNear([near, second], AVIGNON, 20, '')).toHaveLength(2);
  });
});
