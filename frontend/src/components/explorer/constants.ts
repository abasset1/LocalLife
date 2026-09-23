import type { LatLngExpression } from "leaflet";

export const MARSEILLE_LATITUDE = 43.2965;
export const MARSEILLE_LONGITUDE = 5.3698;
export const MARSEILLE_COORDINATES: LatLngExpression = [MARSEILLE_LATITUDE, MARSEILLE_LONGITUDE];

/** Rayon de recherche (LL-4001, km, max 50) tant que la carte n'a pas été déplacée/zoomée. */
export const DEFAULT_SEARCH_RADIUS_KM = 50;

/** Valeur du filtre catégorie représentant « pas de filtre ». */
export const ALL_CATEGORIES = "";

/** Valeur du filtre date représentant « pas de filtre explicite ». */
export const NO_DATE_FILTER = "";

/** Délai (ms) de neutralisation entre la fin d'un geste sur la carte et la recherche qu'il déclenche. */
export const MAP_BOUNDS_DEBOUNCE_MS = 400;
