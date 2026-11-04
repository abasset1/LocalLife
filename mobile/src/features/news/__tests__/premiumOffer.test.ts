import AsyncStorage from '@react-native-async-storage/async-storage';

import { saveFollowNewsPopupSeen } from '../followNews';
import {
  closePremiumOfferForSession,
  isRecentLogin,
  loadNewsSeenBeforeSession,
  loadPremiumOfferOptOut,
  premiumOfferOptOutKey,
  resetPremiumOfferSession,
  savePremiumOfferOptOut,
  shouldShowPremiumOffer,
  wasPremiumOfferClosed,
  type PremiumOfferContext,
} from '../premiumOffer';

jest.mock('@react-native-async-storage/async-storage', () => {
  const store = new Map<string, string>();
  return {
    __esModule: true,
    default: {
      getItem: jest.fn(async (key: string) => store.get(key) ?? null),
      setItem: jest.fn(async (key: string, value: string) => {
        store.set(key, value);
      }),
      clear: jest.fn(async () => store.clear()),
    },
  };
});

const NOW = Date.UTC(2026, 10, 4, 12);
const DAY_S = 24 * 60 * 60;
const nowS = NOW / 1000;

beforeEach(async () => {
  await AsyncStorage.clear();
  resetPremiumOfferSession();
});

const eligible: PremiumOfferContext = {
  userId: 7,
  canFollow: false,
  loggedInAt: nowS - DAY_S,
  nowMs: NOW,
  optedOut: false,
  closedThisSession: false,
  newsSeenBefore: true,
  onboardingShown: false,
};

describe('popup Premium', () => {
  it('se propose à un compte gratuit connecté depuis moins de 7 jours', () => {
    expect(shouldShowPremiumOffer(eligible)).toBe(true);
    expect(shouldShowPremiumOffer({ ...eligible, userId: null })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, canFollow: true })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, canFollow: undefined })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, loggedInAt: nowS - 8 * DAY_S })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, loggedInAt: undefined })).toBe(false);
  });

  it('respecte « Ne plus me proposer », « Plus tard » et l’ordre des popups', () => {
    expect(shouldShowPremiumOffer({ ...eligible, optedOut: true })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, optedOut: null })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, closedThisSession: true })).toBe(false);
    // La popup de nouveauté passe en premier : l'offre attend le lancement suivant.
    expect(shouldShowPremiumOffer({ ...eligible, newsSeenBefore: false })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, newsSeenBefore: null })).toBe(false);
    expect(shouldShowPremiumOffer({ ...eligible, onboardingShown: true })).toBe(false);
  });

  it('compte 7 jours à partir de la connexion', () => {
    expect(isRecentLogin(nowS - 7 * DAY_S + 60, NOW)).toBe(true);
    expect(isRecentLogin(nowS - 7 * DAY_S, NOW)).toBe(false);
  });

  it('mémorise le refus par compte, avec la même clé que le site web', async () => {
    expect(premiumOfferOptOutKey(7)).toBe('locallife.premiumOffer.optOut.7');
    await expect(loadPremiumOfferOptOut(7)).resolves.toBe(false);
    await savePremiumOfferOptOut(7);
    await expect(loadPremiumOfferOptOut(7)).resolves.toBe(true);
    await expect(loadPremiumOfferOptOut(8)).resolves.toBe(false);
  });

  it('ferme la popup pour le lancement en cours seulement', () => {
    expect(wasPremiumOfferClosed(7)).toBe(false);
    closePremiumOfferForSession(7);
    expect(wasPremiumOfferClosed(7)).toBe(true);
    expect(wasPremiumOfferClosed(8)).toBe(false);
  });

  it('lit une seule fois par lancement si la popup de nouveauté était déjà vue', async () => {
    await expect(loadNewsSeenBeforeSession(7)).resolves.toBe(false);
    // La nouveauté s'affiche puis se marque vue : l'offre ne la suit pas pendant ce lancement.
    await saveFollowNewsPopupSeen(7);
    await expect(loadNewsSeenBeforeSession(7)).resolves.toBe(false);
    resetPremiumOfferSession();
    await expect(loadNewsSeenBeforeSession(7)).resolves.toBe(true);
  });
});
