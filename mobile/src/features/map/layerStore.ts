import { useSyncExternalStore } from 'react';

import { type MapLayer, loadMapLayer, saveMapLayer } from './placesPreference';

/**
 * Univers choisi (« Tous », « Lieux », « Food trucks », « Night », issue #226), partagé par la carte et l'onglet
 * « Liste » : passer de l'une à l'autre garde l'univers, comme la ville et les filtres (`explorerStore`).
 *
 * Valeur enregistrée : « Tous » garde la valeur historique `activities` (sur l'appareil, `locallife.mapLayer`, et
 * dans les paramètres, `defaultMapLayer`), pour que les choix déjà mémorisés et l'API restent valables.
 */

let layer: MapLayer = 'activities';
/** Carte choisie dans les paramètres et déjà appliquée : elle passe avant le choix mémorisé sur l'appareil. */
let appliedPreference: MapLayer | null = null;
let loadStarted = false;
const listeners = new Set<() => void>();

function set(next: MapLayer): void {
  if (next === layer) return;
  layer = next;
  listeners.forEach((listener) => listener());
}

export function getMapLayer(): MapLayer {
  return layer;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Lit une fois le choix mémorisé sur l'appareil, sauf si une carte des paramètres s'est appliquée entre-temps. */
export async function initMapLayer(): Promise<void> {
  if (loadStarted) return;
  loadStarted = true;
  const stored = await loadMapLayer();
  if (appliedPreference === null) set(stored);
}

/**
 * Carte choisie dans les paramètres (`null` : aucune, ou visiteur). Appliquée à l'arrivée et chaque fois qu'elle
 * change, sans effacer le choix mémorisé sur l'appareil.
 */
export function applyMapLayerPreference(preferred: MapLayer | null): void {
  if (preferred === null) {
    appliedPreference = null;
    return;
  }
  if (preferred === appliedPreference) return;
  appliedPreference = preferred;
  set(preferred);
}

/** Choix de l'utilisateur (raccourci de la carte ou onglet de la liste) : affiché et mémorisé sur l'appareil. */
export function chooseMapLayer(next: MapLayer): void {
  set(next);
  void saveMapLayer(next);
}

/**
 * Univers réellement affiché : un univers mémorisé mais pas (ou plus) ouvert au lancement revient à « Tous », sans
 * effacer le choix enregistré.
 */
export function shownLayer(chosen: MapLayer, features: { places: boolean; foodTrucks: boolean }): MapLayer {
  if ((chosen === 'places' && !features.places) || (chosen === 'foodTrucks' && !features.foodTrucks)) {
    return 'activities';
  }
  return chosen;
}

/** Remet l'état de départ (tests). */
export function resetMapLayer(): void {
  layer = 'activities';
  appliedPreference = null;
  loadStarted = false;
  listeners.forEach((listener) => listener());
}

export function useMapLayer(): MapLayer {
  return useSyncExternalStore(subscribe, getMapLayer, getMapLayer);
}
