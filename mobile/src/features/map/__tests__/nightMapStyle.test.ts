import { NIGHT_MAP_STYLE, mapStyleFor } from '../nightMapStyle';

describe('fond de carte Night', () => {
  it('n’est violet que pour la carte Night', () => {
    expect(mapStyleFor(true)).toBe(NIGHT_MAP_STYLE);
  });

  it('est retiré par un style vide explicite sur les autres onglets (Android garde sinon le dernier style)', () => {
    expect(mapStyleFor(false)).toEqual([]);
    expect(JSON.stringify(mapStyleFor(false))).toBe('[]');
  });
});
