import { alertCriteria, categoryLabels, toggleCode } from '../options';

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
