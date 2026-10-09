import { formatDateRange, formatPlace, isSafeWebUrl } from '../format';

describe('formatDateRange', () => {
  it('retourne null sans date exploitable', () => {
    expect(formatDateRange(null)).toBeNull();
    expect(formatDateRange('pas une date', null)).toBeNull();
  });

  it('affiche une seule date quand début et fin tombent le même jour', () => {
    const single = formatDateRange('2026-10-12T10:00:00');
    expect(single).toContain('2026');
    expect(formatDateRange('2026-10-12T10:00:00', '2026-10-12T18:00:00')).toBe(single);
  });

  it('affiche une période quand les jours diffèrent', () => {
    expect(formatDateRange('2026-10-12T10:00:00', '2026-10-14T18:00:00')).toMatch(/^du .+ au .+$/);
  });

  it('gère une fin sans début', () => {
    expect(formatDateRange(null, '2026-10-14T18:00:00')).toMatch(/^jusqu'au /);
  });
});

describe('formatPlace', () => {
  it('assemble les parties présentes', () => {
    expect(formatPlace('12 rue X', '13001', 'Marseille')).toBe('12 rue X, 13001 Marseille');
    expect(formatPlace(null, null, 'Marseille')).toBe('Marseille');
    expect(formatPlace(null, null, null)).toBeNull();
  });
});

describe('isSafeWebUrl', () => {
  it("n'accepte que http(s)", () => {
    expect(isSafeWebUrl('https://exemple.fr/agenda')).toBe(true);
    expect(isSafeWebUrl('http://exemple.fr')).toBe(true);
    expect(isSafeWebUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeWebUrl('tel:0612345678')).toBe(false);
    expect(isSafeWebUrl('exemple.fr')).toBe(false);
    expect(isSafeWebUrl(null)).toBe(false);
  });
});
