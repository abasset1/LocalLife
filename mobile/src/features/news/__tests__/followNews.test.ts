import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  followNewsDismissKey,
  loadFollowNewsDismissed,
  saveFollowNewsDismissed,
  shouldShowFollowNews,
} from '../followNews';

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

beforeEach(() => AsyncStorage.clear());

describe('bandeau de nouveauté (suivi)', () => {
  it('ne s’affiche qu’à un membre connu comme gratuit qui ne l’a pas fermé', () => {
    expect(shouldShowFollowNews(7, false, false)).toBe(true);
    expect(shouldShowFollowNews(null, false, false)).toBe(false);
    expect(shouldShowFollowNews(7, true, false)).toBe(false);
    expect(shouldShowFollowNews(7, undefined, false)).toBe(false);
    expect(shouldShowFollowNews(7, false, true)).toBe(false);
    // Fermeture pas encore lue : rien, pour ne pas faire clignoter un bandeau déjà fermé.
    expect(shouldShowFollowNews(7, false, null)).toBe(false);
  });

  it('mémorise la fermeture par compte, comme le site web', async () => {
    expect(followNewsDismissKey(7)).toBe('locallife.news.follow.dismissed.7');
    await expect(loadFollowNewsDismissed(7)).resolves.toBe(false);
    await saveFollowNewsDismissed(7);
    await expect(loadFollowNewsDismissed(7)).resolves.toBe(true);
    await expect(loadFollowNewsDismissed(8)).resolves.toBe(false);
  });
});
