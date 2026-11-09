import { LAST_USED_MAP_LAYER, alertCriteria, categoryLabels, mapLayerOptions, toggleCode } from '../options';

describe('toggleCode', () => {
  it('ajoute puis retire un code', () => {
    expect(toggleCode(['SPORT'], 'CULTURE')).toEqual(['SPORT', 'CULTURE']);
    expect(toggleCode(['SPORT', 'CULTURE'], 'SPORT')).toEqual(['CULTURE']);
  });
});

describe('categoryLabels', () => {
  it('traduit les codes connus et garde les autres tels quels', () => {
    const categories = [{ code: 'SPORT', label: 'Sport' }];
    expect(categoryLabels(['SPORT', 'OLD'], categories)).toBe('Sport, OLD');
  });
});

describe('alertCriteria', () => {
  const categories = [{ code: 'SPORT', label: 'Sport' }];

  it('indique « Toutes catégories » pour une alerte par mots-clés seuls', () => {
    expect(alertCriteria({ categoryCodes: [], includeFoodTrucks: false }, categories)).toBe('Toutes catégories');
  });

  it('ajoute les food trucks aux catégories', () => {
    expect(alertCriteria({ categoryCodes: ['SPORT'], includeFoodTrucks: true }, categories)).toBe(
      'Sport, Food trucks',
    );
    expect(alertCriteria({ categoryCodes: ['SPORT'], includeFoodTrucks: false }, categories)).toBe('Sport');
  });

  it('gère une alerte sur les seuls food trucks', () => {
    expect(alertCriteria({ categoryCodes: [], includeFoodTrucks: true }, categories)).toBe('Food trucks');
  });
});

describe('mapLayerOptions', () => {
  const values = (options: { value: string }[]) => options.map((option) => option.value);

  it('ne propose que les cartes ouvertes (activités et Night toujours)', () => {
    expect(values(mapLayerOptions({ places: false, foodTrucks: false }))).toEqual(['activities', 'night']);
    expect(values(mapLayerOptions({ places: true, foodTrucks: true }))).toEqual([
      'activities',
      'places',
      'foodTrucks',
      'night',
    ]);
  });

  it('garde la carte enregistrée même refermée, et propose « Dernière utilisée »', () => {
    expect(values(mapLayerOptions({ places: false, foodTrucks: false }, 'foodTrucks', true))).toEqual([
      LAST_USED_MAP_LAYER,
      'activities',
      'foodTrucks',
      'night',
    ]);
  });
});
