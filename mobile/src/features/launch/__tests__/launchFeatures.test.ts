import { guideStepsFor } from '@/features/guide/steps';
import { visibleSections } from '@/features/navigation/menuEntries';

import { ALL_CLOSED, ALL_OPEN, hasOpenOffer, parseLaunchFeatures } from '../launchFeatures';

const hrefs = (features = ALL_CLOSED, authenticated = true) =>
  visibleSections({ authenticated, proAccess: false, ownsFoodTrucks: true, features }).flatMap((section) =>
    section.entries.map((entry) => entry.href),
  );

describe('lancement progressif', () => {
  it('lit la réponse du serveur de façon stricte : un champ absent compte comme fermé', () => {
    expect(parseLaunchFeatures({ places: true, premium: 'yes' })).toEqual({
      places: true,
      foodTrucks: false,
      premium: false,
      pro: false,
    });
    expect(parseLaunchFeatures(undefined)).toEqual(ALL_CLOSED);
    expect(hasOpenOffer(ALL_CLOSED)).toBe(false);
    expect(hasOpenOffer({ ...ALL_CLOSED, pro: true })).toBe(true);
  });

  it('masque du menu ce qui demande une fonction fermée, garde le reste du compte gratuit', () => {
    const closed = hrefs();
    for (const hidden of ['/contribution', '/suivis', '/abonnement', '/nouveautes', '/pro/foodtrucks']) {
      expect(closed).not.toContain(hidden);
    }
    for (const kept of ['/', '/liste', '/favoris', '/alertes', '/parametres', '/contributions', '/compte', '/guide']) {
      expect(closed).toContain(kept);
    }
    // Section « Communauté » vidée : elle disparaît.
    expect(
      visibleSections({ authenticated: false, proAccess: false, features: ALL_CLOSED }).map((s) => s.title),
    ).not.toContain('Communauté');
  });

  it('rouvre chaque rubrique avec sa fonction', () => {
    expect(hrefs({ ...ALL_CLOSED, premium: true })).toEqual(
      expect.arrayContaining(['/contribution', '/suivis', '/abonnement', '/nouveautes']),
    );
    expect(hrefs({ ...ALL_CLOSED, pro: true })).not.toContain('/pro/foodtrucks');
    expect(hrefs({ ...ALL_CLOSED, pro: true, foodTrucks: true })).toContain('/pro/foodtrucks');
    expect(hrefs(ALL_OPEN)).toEqual(expect.arrayContaining(['/contribution', '/abonnement', '/pro/foodtrucks']));
  });

  it('retire du guide les étapes des fonctions fermées', () => {
    const ids = (features = ALL_CLOSED) => guideStepsFor(features).map((step) => step.id);
    expect(ids()).not.toContain('couches');
    expect(ids()).not.toContain('contribuer');
    expect(ids()).not.toContain('offres');
    expect(ids()).toContain('bienvenue');
    expect(ids({ ...ALL_CLOSED, foodTrucks: true })).toContain('couches');
    expect(guideStepsFor()).toHaveLength(guideStepsFor(ALL_OPEN).length);
  });
});
