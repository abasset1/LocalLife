import type { Activity, FoodTruck, Place } from '@/api';

import { listItemKey, listSummary, mergeByDistance, placesNear } from '../placeList';

const origin = { latitude: 43.9493, longitude: 4.8055 };

function place(id: number, name: string, latitude: number, longitude: number): Place {
  return {
    id,
    name,
    placeType: 'Museum',
    address: null,
    postalCode: null,
    city: 'Avignon',
    latitude,
    longitude,
    description: null,
    website: null,
    coordinatePrecision: 'EXACT',
  } as Place;
}

describe('liste : lieux et mélange « Tous »', () => {
  it('garde les lieux du rayon, filtrés par le texte, du plus proche au plus loin', () => {
    const near = place(1, 'Palais des Papes', 43.9508, 4.8075);
    const closer = place(2, 'Musée Calvet', 43.9493, 4.8060);
    const far = place(3, 'Pont du Gard', 43.947, 4.535);
    expect(placesNear([near, far, closer], origin, 10, '').map((p) => p.place.id)).toEqual([2, 1]);
    expect(placesNear([near, far, closer], origin, 30, '').map((p) => p.place.id)).toEqual([2, 1, 3]);
    expect(placesNear([near, far, closer], origin, 30, 'palais').map((p) => p.place.id)).toEqual([1]);
  });

  it('mêle activités, lieux et food trucks par distance', () => {
    const activity = { id: 7 } as Activity;
    const truck = { presenceId: 9 } as FoodTruck;
    const merged = mergeByDistance(
      [{ kind: 'activity', activity, km: 2 }],
      [{ kind: 'place', place: place(1, 'Palais', 0, 0), km: 0.5 }],
      [{ kind: 'foodTruck', foodTruck: truck, km: 1 }],
    );
    expect(merged.map(listItemKey)).toEqual(['p-1', 't-9', 'a-7']);
  });

  it('résume la liste selon l’univers', () => {
    expect(listSummary('activities', 12, 20)).toBe('12 activités à moins de 20 km');
    expect(listSummary('places', 1, 10)).toBe('1 lieu à moins de 10 km');
    expect(listSummary('places', 3, 10)).toBe('3 lieux à moins de 10 km');
    expect(listSummary('night', 2, 20)).toBe('2 sorties de nuit à moins de 20 km');
    expect(listSummary('foodTrucks', 0, 20)).toBe('Aucun food truck présent aujourd’hui');
    expect(listSummary('activities', 0, 20)).toBe('Aucun résultat');
  });
});
