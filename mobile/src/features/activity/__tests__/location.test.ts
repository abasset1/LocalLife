import { buildDirectionsUrl, isApproximateLocation, withApproximateMention } from '../location';

const exact = { latitude: 43.95, longitude: 4.8, address: '8 bis rue Sainte-Catherine', city: 'Avignon' };

describe('isApproximateLocation', () => {
  it('ne signale que la précision APPROXIMATE', () => {
    expect(isApproximateLocation({ coordinatePrecision: 'APPROXIMATE' })).toBe(true);
    expect(isApproximateLocation({ coordinatePrecision: 'UNKNOWN' })).toBe(false);
    expect(isApproximateLocation({})).toBe(false);
  });
});

describe('withApproximateMention', () => {
  it('ajoute la mention au lieu affiché', () => {
    expect(withApproximateMention('Avignon', { coordinatePrecision: 'APPROXIMATE' })).toBe(
      'Avignon (emplacement approximatif)',
    );
    expect(withApproximateMention(null, { coordinatePrecision: 'APPROXIMATE' })).toBe('Emplacement approximatif');
  });

  it('laisse le lieu inchangé sinon', () => {
    expect(withApproximateMention('Avignon', { coordinatePrecision: 'EXACT' })).toBe('Avignon');
    expect(withApproximateMention(null, {})).toBeNull();
  });
});

describe('buildDirectionsUrl', () => {
  it('vise les coordonnées pour une position exacte ou inconnue', () => {
    expect(buildDirectionsUrl(exact)).toBe('https://www.google.com/maps/dir/?api=1&destination=43.95%2C4.8');
    expect(buildDirectionsUrl({ ...exact, coordinatePrecision: 'UNKNOWN' })).toContain('43.95%2C4.8');
  });

  it("vise l'adresse ou la commune pour une position approximative", () => {
    expect(buildDirectionsUrl({ ...exact, address: null, coordinatePrecision: 'APPROXIMATE' })).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=Avignon',
    );
  });

  it('garde les coordonnées si une position approximative est sans texte', () => {
    expect(
      buildDirectionsUrl({ ...exact, address: null, city: null, coordinatePrecision: 'APPROXIMATE' }),
    ).toContain('43.95%2C4.8');
  });

  it("renvoie null sans aucune destination", () => {
    expect(buildDirectionsUrl({ latitude: null, longitude: null, address: null, city: null })).toBeNull();
  });
});
