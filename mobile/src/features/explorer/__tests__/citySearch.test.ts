import { cityLabel, formatPostalCodes, isCityQuery } from '../citySearch';

describe('recherche de ville', () => {
  it('ne cherche qu’à partir de 2 caractères utiles', () => {
    expect(isCityQuery('')).toBe(false);
    expect(isCityQuery(' l ')).toBe(false);
    expect(isCityQuery('ly')).toBe(true);
    expect(isCityQuery('69001')).toBe(true);
  });

  it('affiche les codes postaux d’une commune, ou celui saisi', () => {
    expect(formatPostalCodes([])).toBe('');
    expect(formatPostalCodes(['84000'])).toBe('84000');
    expect(formatPostalCodes(['69001', '69005', '69009'])).toBe('69001 – 69009');
    expect(formatPostalCodes(['69001', '69005', '69009'], '69005')).toBe('69005');
  });

  it('nomme la commune choisie avec son département quand il est connu', () => {
    const lyon = { name: 'Lyon', postalCodes: ['69001'], latitude: 45.7, longitude: 4.8 };
    expect(cityLabel({ ...lyon, department: '69' })).toBe('Lyon (69)');
    expect(cityLabel({ ...lyon, department: null })).toBe('Lyon');
  });
});
