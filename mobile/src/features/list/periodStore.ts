import { useSyncExternalStore } from 'react';

import type { Period } from './filters';

/**
 * Période choisie (« Tout », « Aujourd'hui », « Ce week-end », « 7 jours »), partagée par la carte et
 * l'onglet « Liste » : un choix fait sur l'un s'applique à l'autre. Gardée en mémoire pour la session.
 */
let current: Period = 'all';
const listeners = new Set<() => void>();

export function setPeriod(period: Period): void {
  if (period === current) return;
  current = period;
  listeners.forEach((listener) => listener());
}

export function getPeriod(): Period {
  return current;
}

/** Prévenu à chaque changement de période ; renvoie la fonction de désabonnement. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function usePeriod(): [Period, (period: Period) => void] {
  const period = useSyncExternalStore(subscribe, getPeriod, getPeriod);
  return [period, setPeriod];
}
