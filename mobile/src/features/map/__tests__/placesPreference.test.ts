import AsyncStorage from '@react-native-async-storage/async-storage';

import { SHOW_PLACES_KEY, loadShowPlaces, saveShowPlaces } from '../placesPreference';

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

describe('choix de la couche « Lieux »', () => {
  it('est masquée par défaut', async () => {
    await expect(loadShowPlaces()).resolves.toBe(false);
  });

  it('mémorise le choix', async () => {
    await saveShowPlaces(true);
    expect(await AsyncStorage.getItem(SHOW_PLACES_KEY)).toBe('true');
    await expect(loadShowPlaces()).resolves.toBe(true);
    await saveShowPlaces(false);
    await expect(loadShowPlaces()).resolves.toBe(false);
  });

  it('reste utilisable si le stockage échoue', async () => {
    jest.spyOn(AsyncStorage, 'getItem').mockRejectedValueOnce(new Error('indisponible'));
    jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('indisponible'));
    await expect(loadShowPlaces()).resolves.toBe(false);
    await expect(saveShowPlaces(true)).resolves.toBeUndefined();
  });
});
