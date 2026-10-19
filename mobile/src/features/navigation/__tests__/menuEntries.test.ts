import { MENU_SECTIONS, isCurrentEntry } from '../menuEntries';

const all = MENU_SECTIONS.flatMap((section) => section.entries);

describe('menu rétractable', () => {
  it('garde l’accès à toutes les rubriques des anciens onglets et du compte', () => {
    expect(all.map((entry) => entry.href).sort()).toEqual(
      [
        '/', '/abonnement', '/alertes', '/compte', '/contribution', '/contributions', '/favoris', '/liste',
        '/parametres', '/pro',
      ].sort(),
    );
  });

  it('réserve « Mon espace » aux utilisateurs connectés, le reste est visible de tous', () => {
    const reserved = MENU_SECTIONS.filter((section) => section.authenticated).flatMap((s) => s.entries);
    expect(reserved.map((entry) => entry.href)).toEqual([
      '/favoris', '/contributions', '/alertes', '/parametres', '/pro', '/abonnement',
    ]);
  });

  it('reconnaît la rubrique de l’écran affiché', () => {
    const carte = all.find((entry) => entry.href === '/')!;
    const liste = all.find((entry) => entry.href === '/liste')!;
    expect(isCurrentEntry(carte, '/')).toBe(true);
    expect(isCurrentEntry(carte, '/liste')).toBe(false);
    expect(isCurrentEntry(liste, '/liste')).toBe(true);
  });
});
