import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  applyMapLayerPreference,
  chooseMapLayer,
  getMapLayer,
  initMapLayer,
  resetMapLayer,
  shownLayer,
} from '../layerStore';
import { MAP_LAYER_KEY } from '../placesPreference';

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

beforeEach(async () => {
  resetMapLayer();
  await AsyncStorage.clear();
});

describe('univers partagé par la carte et la liste', () => {
  it('commence sur les activités et reprend le choix mémorisé sur l’appareil', async () => {
    expect(getMapLayer()).toBe('activities');
    await AsyncStorage.setItem(MAP_LAYER_KEY, 'night');
    await initMapLayer();
    expect(getMapLayer()).toBe('night');
  });

  it('mémorise le choix de l’utilisateur', async () => {
    chooseMapLayer('foodTrucks');
    expect(getMapLayer()).toBe('foodTrucks');
    await new Promise((resolve) => setImmediate(resolve));
    expect(await AsyncStorage.getItem(MAP_LAYER_KEY)).toBe('foodTrucks');
  });

  it('la carte des paramètres passe avant le choix mémorisé, sans l’effacer', async () => {
    await AsyncStorage.setItem(MAP_LAYER_KEY, 'night');
    applyMapLayerPreference('places');
    await initMapLayer();
    expect(getMapLayer()).toBe('places');
    expect(await AsyncStorage.getItem(MAP_LAYER_KEY)).toBe('night');
    // Même paramètre relu (retour sur l'écran) : un choix fait entre-temps n'est pas écrasé.
    chooseMapLayer('foodTrucks');
    applyMapLayerPreference('places');
    expect(getMapLayer()).toBe('foodTrucks');
  });

  it('revient aux activités pour un contenu pas encore ouvert', () => {
    expect(shownLayer('places', { places: false, foodTrucks: true })).toBe('activities');
    expect(shownLayer('foodTrucks', { places: true, foodTrucks: false })).toBe('activities');
    expect(shownLayer('night', { places: false, foodTrucks: false })).toBe('night');
  });
});
