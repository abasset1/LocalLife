import { MENU_SECTIONS, isCurrentEntry, visibleSections } from '../menuEntries';

const all = MENU_SECTIONS.flatMap((section) => section.entries);

describe('menu rétractable', () => {
  it('garde l’accès à toutes les rubriques des anciens onglets et du compte', () => {
    expect(all.map((entry) => entry.href).sort()).toEqual(
      [
        '/', '/abonnement', '/alertes', '/compte', '/contribution', '/contributions', '/favoris', '/liste',
        '/parametres', '/pro', '/pro/foodtrucks',
      ].sort(),
    );
  });

  it('réserve « Mon espace » aux utilisateurs connectés, le reste est visible de tous', () => {
    const reserved = MENU_SECTIONS.filter((section) => section.authenticated).flatMap((s) => s.entries);
    expect(reserved.map((entry) => entry.href)).toEqual([
      '/favoris', '/contributions', '/alertes', '/parametres', '/pro', '/pro/foodtrucks', '/abonnement',
    ]);
  });

  it('n’affiche l’espace professionnel qu’avec un accès pro', () => {
    const hrefs = (access: { authenticated: boolean; proAccess: boolean }) =>
      visibleSections(access).flatMap((section) => section.entries.map((entry) => entry.href));
    expect(hrefs({ authenticated: true, proAccess: false })).not.toContain('/pro');
    expect(hrefs({ authenticated: true, proAccess: false })).toContain('/abonnement');
    expect(hrefs({ authenticated: true, proAccess: true })).toContain('/pro');
    expect(hrefs({ authenticated: false, proAccess: false })).not.toContain('/favoris');
  });

  it('garde « Mes food trucks » pour un ancien compte Pro qui en possède encore', () => {
    const hrefs = (access: { authenticated: boolean; proAccess: boolean; ownsFoodTrucks?: boolean }) =>
      visibleSections(access).flatMap((section) => section.entries.map((entry) => entry.href));
    expect(hrefs({ authenticated: true, proAccess: false, ownsFoodTrucks: true })).toContain('/pro/foodtrucks');
    expect(hrefs({ authenticated: true, proAccess: false, ownsFoodTrucks: false })).not.toContain('/pro/foodtrucks');
    // Avec un accès pro, ils restent dans l'espace professionnel.
    expect(hrefs({ authenticated: true, proAccess: true, ownsFoodTrucks: true })).not.toContain('/pro/foodtrucks');
  });

  it('reconnaît la rubrique de l’écran affiché', () => {
    const carte = all.find((entry) => entry.href === '/')!;
    const liste = all.find((entry) => entry.href === '/liste')!;
    expect(isCurrentEntry(carte, '/')).toBe(true);
    expect(isCurrentEntry(carte, '/liste')).toBe(false);
    expect(isCurrentEntry(liste, '/liste')).toBe(true);
  });
});
