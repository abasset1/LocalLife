import AsyncStorage from '@react-native-async-storage/async-storage';

import type { MapLayerValue } from '@/api';

/**
 * Contenu affiché sur la carte : activités, lieux (ADR-0011, phase B) ou food trucks (ADR-0013), un seul à
 * la fois — comme sur le site web.
 */
export type MapLayer = MapLayerValue;

/** Clé du contenu choisi, comme `locallife.mapLayer` sur le site web. */
export const MAP_LAYER_KEY = 'locallife.mapLayer';

const MAP_LAYERS: readonly string[] = ['activities', 'places', 'foodTrucks'];

/** Carte choisie dans les paramètres (`defaultMapLayer`), si elle est valide ; `null` sinon. */
export function preferredMapLayer(value: unknown): MapLayer | null {
  return typeof value === 'string' && MAP_LAYERS.includes(value) ? (value as MapLayer) : null;
}

/** Choix mémorisé sur l'appareil ; les activités s'il est absent ou illisible (la carte reste utilisable). */
export async function loadMapLayer(): Promise<MapLayer> {
  try {
    const stored = await AsyncStorage.getItem(MAP_LAYER_KEY);
    return stored !== null && MAP_LAYERS.includes(stored) ? (stored as MapLayer) : 'activities';
  } catch {
    return 'activities';
  }
}

/** Mémorise le choix ; un échec d'écriture est sans conséquence (le choix vaut pour la session). */
export async function saveMapLayer(layer: MapLayer): Promise<void> {
  try {
    await AsyncStorage.setItem(MAP_LAYER_KEY, layer);
  } catch {
    // Stockage indisponible : le choix n'est pas mémorisé.
  }
}
