import { shouldOfferGuide } from '../guideOffer';

// Remonté avant les imports par Jest : le stockage natif n'existe pas sous Jest.
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: jest.fn(), setItem: jest.fn() }));

describe('shouldOfferGuide', () => {
  const now = 1_800_000_000_000;

  it('propose le didacticiel juste après une connexion, une seule fois', () => {
    expect(shouldOfferGuide(false, now / 1000 - 30, now)).toBe(true);
    expect(shouldOfferGuide(true, now / 1000 - 30, now)).toBe(false);
  });

  it('ne le propose pas pour une session restaurée au lancement', () => {
    expect(shouldOfferGuide(false, now / 1000 - 3600, now)).toBe(false);
    expect(shouldOfferGuide(false, undefined, now)).toBe(false);
  });
});
