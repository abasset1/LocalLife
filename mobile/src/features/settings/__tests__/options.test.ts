import { categoryLabels, toggleCode } from '../options';

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
