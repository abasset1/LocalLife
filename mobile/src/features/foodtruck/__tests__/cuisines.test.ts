import { availableCuisines, cuisineLabel, markerOf } from '../cuisines';

describe('pastille et type de cuisine des food trucks', () => {
  it('prend la pastille choisie, sinon l’emoji du type de cuisine, sinon le camion', () => {
    expect(markerOf({ markerEmoji: '🔥', markerColor: '#16a34a', cuisineType: 'PIZZA' })).toEqual({
      emoji: '🔥',
      color: '#16a34a',
    });
    expect(markerOf({ cuisineType: 'PIZZA' })).toEqual({ emoji: '🍕', color: '#ec3b6e' });
    expect(markerOf({ markerColor: 'rouge' })).toEqual({ emoji: '🚚', color: '#ec3b6e' });
  });

  it('propose seulement les types de cuisine présents', () => {
    expect(availableCuisines([{ cuisineType: 'ASIAN' }, { cuisineType: null }, { cuisineType: 'BURGER' }]).map((c) => c.code))
      .toEqual(['BURGER', 'ASIAN']);
    expect(cuisineLabel('CREPES')).toBe('Crêpes et galettes');
    expect(cuisineLabel(null)).toBeNull();
  });
});
