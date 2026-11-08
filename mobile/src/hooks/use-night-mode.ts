import { useSyncExternalStore } from 'react';

/**
 * Thème « Night » : actif tant que la carte Night est choisie sur l'écran Carte, comme sur le site (`useNightTheme`).
 * Toute l'application passe alors en violet nuit avec des accents néon, et le logo devient le bouc néon. Il revient au
 * thème habituel (clair ou sombre) dès que l'on choisit une autre carte.
 */
let nightMode = false;
const listeners = new Set<() => void>();

export function setNightMode(enabled: boolean): void {
  if (nightMode === enabled) return;
  nightMode = enabled;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Thème Night actif ? (lecture hors composant, et pour les tests). */
export function isNightMode(): boolean {
  return nightMode;
}

export function subscribeNightMode(listener: () => void): () => void {
  return subscribe(listener);
}

function getSnapshot(): boolean {
  return nightMode;
}

export function useNightMode(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
