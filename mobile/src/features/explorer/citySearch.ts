/**
 * Règles de la recherche de ville (FRONT-REV-02), sans dépendance à React Native pour rester testables.
 */
import type { CitySuggestion } from '@/api';

/** En dessous, aucune commune n'est cherchée (même seuil que le backend et le site web). */
export const MIN_CITY_QUERY_LENGTH = 2;

/** Pause de frappe avant d'interroger le serveur. */
export const CITY_SEARCH_DEBOUNCE_MS = 250;

/** Saisie assez longue pour lancer une recherche. */
export function isCityQuery(query: string): boolean {
  return query.trim().length >= MIN_CITY_QUERY_LENGTH;
}

/**
 * Codes postaux lisibles : « 69003 », ou « 69001 – 69009 » pour une ville qui en a plusieurs. Si la
 * saisie est l'un de ces codes postaux, c'est lui qui est affiché (comme sur le site web).
 */
export function formatPostalCodes(postalCodes: readonly string[], query = ''): string {
  const typed = query.trim();
  if (postalCodes.includes(typed)) return typed;
  if (postalCodes.length === 0) return '';
  if (postalCodes.length === 1) return postalCodes[0];
  return `${postalCodes[0]} – ${postalCodes[postalCodes.length - 1]}`;
}

/** Nom affiché d'une commune choisie : « Lyon », « Saint-Denis (93) » quand le département est connu. */
export function cityLabel(city: CitySuggestion): string {
  return city.department ? `${city.name} (${city.department})` : city.name;
}
