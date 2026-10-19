import AsyncStorage from '@react-native-async-storage/async-storage';

import { MAP_LAYER_KEY, loadMapLayer, saveMapLayer } from '../placesPreference';

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

describe('contenu de la carte', () => {
  it('affiche les activités par défaut', async () => {
    await expect(loadMapLayer()).resolves.toBe('activities');
  });

  it('mémorise le choix', async () => {
    await saveMapLayer('places');
    expect(await AsyncStorage.getItem(MAP_LAYER_KEY)).toBe('places');
    await expect(loadMapLayer()).resolves.toBe('places');
    await saveMapLayer('foodTrucks');
    await expect(loadMapLayer()).resolves.toBe('foodTrucks');
  });

  it('ignore une valeur inconnue', async () => {
    await AsyncStorage.setItem(MAP_LAYER_KEY, 'true');
    await expect(loadMapLayer()).resolves.toBe('activities');
  });

  it('reste utilisable si le stockage échoue', async () => {
    jest.spyOn(AsyncStorage, 'getItem').mockRejectedValueOnce(new Error('indisponible'));
    jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('indisponible'));
    await expect(loadMapLayer()).resolves.toBe('activities');
    await expect(saveMapLayer('places')).resolves.toBeUndefined();
  });
});
