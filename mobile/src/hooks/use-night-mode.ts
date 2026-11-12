import { useSyncExternalStore } from 'react';

/**
 * Thème « Night » : actif tant que la carte Night est choisie sur l'écran Carte, comme sur le site (`useNightTheme`).
 * Toute l'application passe alors en violet nuit avec des accents néon, et le logo devient le bouc néon. Il revient au
 * thème habituel (clair ou sombre) dès que l'on choisit une autre carte.
 */
let nightMode = false;
/** Écrans qui imposent le thème habituel (accueil de première connexion) : Night suspendu tant qu'il y en a un. */
let suspensions = 0;
const listeners = new Set<() => void>();

function effective(): boolean {
  return nightMode && suspensions === 0;
}

function update(change: () => void): void {
  const before = effective();
  change();
  if (effective() !== before) listeners.forEach((listener) => listener());
}

export function setNightMode(enabled: boolean): void {
  update(() => {
    nightMode = enabled;
  });
}

/**
 * Impose le thème habituel (clair ou sombre, logo Neiri) le temps d'un écran, sans oublier la carte Night choisie :
 * la fonction renvoyée lève la suspension, et Night revient si la carte Night est toujours choisie.
 */
export function suspendNightMode(): () => void {
  update(() => {
    suspensions += 1;
  });
  let released = false;
  return () => {
    if (released) return;
    released = true;
    update(() => {
      suspensions -= 1;
    });
  };
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Thème Night actif ? (lecture hors composant, et pour les tests). */
export function isNightMode(): boolean {
  return effective();
}

export function subscribeNightMode(listener: () => void): () => void {
  return subscribe(listener);
}

function getSnapshot(): boolean {
  return effective();
}

export function useNightMode(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
