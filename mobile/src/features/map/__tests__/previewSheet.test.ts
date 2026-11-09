import { formatWhen, placeWithDistance, sheetMoveFor } from '../previewSheet';

describe('panneau d’aperçu', () => {
  it('développe, réduit ou ferme selon le glissement de la poignée', () => {
    expect(sheetMoveFor(-50, false)).toBe('expand');
    expect(sheetMoveFor(-50, true)).toBeNull();
    expect(sheetMoveFor(50, true)).toBe('collapse');
    expect(sheetMoveFor(50, false)).toBe('close');
    expect(sheetMoveFor(10, false)).toBeNull();
    expect(sheetMoveFor(-10, true)).toBeNull();
  });

  it('écrit une date courte, avec l’heure seulement si elle est connue', () => {
    const now = new Date('2026-11-08T10:00:00');
    expect(formatWhen('2026-11-08T08:00:00', '2026-11-08T13:00:00', now)).toBe('Aujourd’hui · 8 h – 13 h');
    expect(formatWhen('2026-11-09T21:30:00', null, now)).toBe('Demain · 21 h 30');
    expect(formatWhen('2026-11-08', null, now)).toBe('Aujourd’hui');
    expect(formatWhen('2026-11-08T00:00:00', null, now)).toBe('Aujourd’hui');
    expect(formatWhen('2026-11-14T18:30:00', null, now)).toMatch(/14 nov\. · 18 h 30$/);
    // Fin sur plusieurs jours (festival) : pas d'heure de fin trompeuse.
    expect(formatWhen('2026-11-08T08:00:00', '2026-11-12T18:00:00', now)).toBe('Aujourd’hui · 8 h');
    expect(formatWhen(null, null, now)).toBeNull();
    expect(formatWhen('pas une date', null, now)).toBeNull();
  });

  it('n’ajoute la distance que si la position est connue', () => {
    const target = { latitude: 43.9493, longitude: 4.8055 };
    expect(placeWithDistance('Avignon', target, null)).toBe('Avignon');
    expect(placeWithDistance('Avignon', target, { latitude: 43.9493, longitude: 4.8117 })).toBe('Avignon · 500 m');
    expect(placeWithDistance(null, target, null)).toBeNull();
  });
});
