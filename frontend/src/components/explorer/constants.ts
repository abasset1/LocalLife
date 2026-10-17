import type { LatLngExpression } from "leaflet";

/**
 * Vue d'ensemble affichée tant qu'aucun lieu n'est choisi (spec P0-A) : la zone couverte par les
 * sources actuelles (Provence). Ce n'est jamais une position supposée de l'utilisateur : aucune
 * recherche de proximité n'est centrée dessus, la recherche porte sur la zone affichée.
 */
export const DEFAULT_MAP_CENTER: LatLngExpression = [43.65, 5.2];
export const DEFAULT_MAP_ZOOM = 8;

/** Rayon de recherche (LL-4001, km, max 50) tant que la carte n'a pas été déplacée/zoomée. */
export const DEFAULT_SEARCH_RADIUS_KM = 50;

/** Valeur du filtre date représentant « pas de filtre explicite ». */
export const NO_DATE_FILTER = "";

/** Délai (ms) de neutralisation entre la fin d'un geste sur la carte et la recherche qu'il déclenche. */
export const MAP_BOUNDS_DEBOUNCE_MS = 400;
