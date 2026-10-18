import { useSyncExternalStore } from 'react';

import type { UserPreferences } from '@/api';

/**
 * Critères de l'explorateur partagés par la carte et l'onglet « Liste » (FRONT-REV-02) : le lieu de
 * recherche, les catégories cochées et le rayon. Un choix fait sur l'une des vues vaut pour l'autre et
 * survit aux changements de vue, pour la session (comme la période, voir `periodStore`).
 */

/** Lieu de recherche : ville choisie, position de l'appareil, ville des paramètres ou zone de la carte. */
export interface ExplorerPlace {
  label: string;
  latitude: number;
  longitude: number;
  source: 'city' | 'position' | 'preferences' | 'map';
}

export interface ExplorerState {
  /** `null` : aucun lieu choisi, jamais de ville supposée à la place. */
  place: ExplorerPlace | null;
  /** Codes de catégories cochés ; vide = toutes les catégories. */
  categoryCodes: readonly string[];
  /** Rayon (km) ; `null` = sans rayon (la carte cherche dans la zone visible). */
  radiusKm: number | null;
}

const INITIAL: ExplorerState = { place: null, categoryCodes: [], radiusKm: null };

let state: ExplorerState = INITIAL;
/** Paramètres de recherche déjà appliqués (JSON), pour ne les appliquer qu'à leur changement. */
let appliedPreferencesKey: string | null = null;
const listeners = new Set<() => void>();

function update(next: Partial<ExplorerState>): void {
  state = { ...state, ...next };
  listeners.forEach((listener) => listener());
}

export function getExplorerState(): ExplorerState {
  return state;
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function setPlace(place: ExplorerPlace | null): void {
  update({ place });
}

export function toggleCategory(code: string): void {
  const codes = state.categoryCodes;
  update({ categoryCodes: codes.includes(code) ? codes.filter((c) => c !== code) : [...codes, code] });
}

export function clearCategories(): void {
  if (state.categoryCodes.length > 0) update({ categoryCodes: [] });
}

export function setRadius(radiusKm: number | null): void {
  update({ radiusKm });
}

function hasValidCity(preferences: UserPreferences): boolean {
  const { defaultLatitude: latitude, defaultLongitude: longitude } = preferences;
  return (
    latitude !== null &&
    longitude !== null &&
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    Math.abs(latitude) <= 90 &&
    Math.abs(longitude) <= 180
  );
}

/**
 * Paramètres de recherche de l'utilisateur connecté (`null` : visiteur) : appliqués quand ils changent
 * (connexion, retour de l'écran « Paramètres »), pas à chaque lecture. Catégories et rayon reprennent
 * ceux des paramètres ; leur ville devient le lieu de recherche, sauf si l'utilisateur a choisi une ville
 * ou sa position entre-temps. Renvoie `true` si quelque chose a été appliqué.
 */
export function applyPreferences(preferences: UserPreferences | null): boolean {
  const key = preferences ? JSON.stringify(preferences) : null;
  if (key === appliedPreferencesKey) return false;
  appliedPreferencesKey = key;
  const keepPlace = state.place !== null && state.place.source !== 'preferences';
  if (!preferences) {
    // Déconnexion : les critères tirés des paramètres ne valent plus.
    update({ categoryCodes: [], radiusKm: null, place: keepPlace ? state.place : null });
    return true;
  }
  let place = keepPlace ? state.place : null;
  if (!keepPlace && hasValidCity(preferences)) {
    place = {
      label: preferences.defaultCity ?? 'votre ville',
      latitude: preferences.defaultLatitude as number,
      longitude: preferences.defaultLongitude as number,
      source: 'preferences',
    };
  }
  update({ categoryCodes: preferences.defaultCategoryCodes, radiusKm: preferences.defaultRadiusKm, place });
  return true;
}

/** Remet l'état de départ (tests). */
export function resetExplorer(): void {
  state = INITIAL;
  appliedPreferencesKey = null;
  listeners.forEach((listener) => listener());
}

export function useExplorer(): ExplorerState {
  return useSyncExternalStore(subscribe, getExplorerState, getExplorerState);
}
