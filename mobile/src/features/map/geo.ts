/**
 * Calculs géographiques purs de la carte (LL-MOB-0005), sans dépendance à
 * React Native, pour rester testables.
 */

export interface Region {
  latitude: number;
  longitude: number;
  latitudeDelta: number;
  longitudeDelta: number;
}

/** Zone rectangulaire, dans les noms de paramètres du contrat `within-bounds`. */
export interface Bounds {
  swLatitude: number;
  swLongitude: number;
  neLatitude: number;
  neLongitude: number;
}

/** Niveau de zoom maximal utilisé pour le clustering et le recentrage. */
export const MAX_ZOOM = 20;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const round5 = (value: number) => Math.round(value * 1e5) / 1e5;

/**
 * Convertit la région visible en bounding box conforme au contrat
 * `within-bounds` : latitudes dans [-90, 90], longitudes dans [-180, 180],
 * `sw < ne` strictement, sans passage de l'antiméridien (non supporté par
 * le backend → 400). Retourne `null` si la zone est inutilisable.
 */
export function regionToBounds(region: Region): Bounds | null {
  const values = [region.latitude, region.longitude, region.latitudeDelta, region.longitudeDelta];
  if (values.some((v) => !Number.isFinite(v))) return null;

  const bounds: Bounds = {
    swLatitude: round5(clamp(region.latitude - region.latitudeDelta / 2, -90, 90)),
    swLongitude: round5(clamp(region.longitude - region.longitudeDelta / 2, -180, 180)),
    neLatitude: round5(clamp(region.latitude + region.latitudeDelta / 2, -90, 90)),
    neLongitude: round5(clamp(region.longitude + region.longitudeDelta / 2, -180, 180)),
  };

  if (bounds.swLatitude >= bounds.neLatitude || bounds.swLongitude >= bounds.neLongitude) {
    return null;
  }
  return bounds;
}

/**
 * Étendue maximale (en degrés, ~650 km en latitude) d'une zone envoyée à
 * `within-bounds`. Assez large pour la vue d'ensemble d'une région, comme sur
 * le site web (qui n'a pas de limite) ; l'endpoint n'ayant ni pagination ni
 * plafond, la carte demande encore de zoomer à l'échelle d'un pays.
 */
export const MAX_SEARCH_SPAN_DEGREES = 6;

/** Étendue maximale pour la couche « Lieux » (~220 km), plus dense : inchangée. */
export const PLACES_MAX_SPAN_DEGREES = 2;

export function isSearchable(bounds: Bounds, maxSpan: number = MAX_SEARCH_SPAN_DEGREES): boolean {
  return bounds.neLatitude - bounds.swLatitude <= maxSpan && bounds.neLongitude - bounds.swLongitude <= maxSpan;
}

/** `true` si `inner` est entièrement contenue dans `outer`. */
export function boundsContain(outer: Bounds, inner: Bounds): boolean {
  return (
    inner.swLatitude >= outer.swLatitude &&
    inner.swLongitude >= outer.swLongitude &&
    inner.neLatitude <= outer.neLatitude &&
    inner.neLongitude <= outer.neLongitude
  );
}

export function pointInBounds(bounds: Bounds, latitude: number, longitude: number): boolean {
  return (
    latitude >= bounds.swLatitude &&
    latitude <= bounds.neLatitude &&
    longitude >= bounds.swLongitude &&
    longitude <= bounds.neLongitude
  );
}

/** Niveau de zoom (tuiles web, 0–20) correspondant à la largeur visible. */
export function zoomForRegion(region: Region): number {
  if (!(region.longitudeDelta > 0)) return MAX_ZOOM;
  return clamp(Math.floor(Math.log2(360 / region.longitudeDelta)), 0, MAX_ZOOM);
}

/** Région centrée sur un point au niveau de zoom voulu, en gardant le ratio de `like`. */
export function regionForZoom(
  latitude: number,
  longitude: number,
  zoom: number,
  like: Region,
): Region {
  const longitudeDelta = 360 / 2 ** clamp(zoom, 0, MAX_ZOOM);
  const ratio = like.longitudeDelta > 0 ? like.latitudeDelta / like.longitudeDelta : 1;
  return { latitude, longitude, latitudeDelta: longitudeDelta * ratio, longitudeDelta };
}

/** Kilomètres par degré de latitude (approximation sphérique, suffisante pour cadrer la carte). */
const KM_PER_DEGREE = 111.32;

/**
 * Région centrée sur une ville et couvrant un disque de `radiusKm` (cadrage
 * initial de la carte d'après les paramètres de recherche).
 */
export function regionForRadius(latitude: number, longitude: number, radiusKm: number): Region {
  const latitudeDelta = (2 * radiusKm) / KM_PER_DEGREE;
  const cosine = Math.cos((latitude * Math.PI) / 180);
  const longitudeDelta = cosine > 0.01 ? latitudeDelta / cosine : latitudeDelta;
  return { latitude, longitude, latitudeDelta, longitudeDelta };
}

/** En dessous de cette étendue (en degrés, ~50 m), des points sont considérés comme au même endroit. */
export const SAME_PLACE_SPAN_DEGREES = 0.0005;

/**
 * Région qui cadre tous les points (avec une marge), en gardant le ratio de `like` : appui sur un
 * groupe de marqueurs, comme sur le site web. `null` si les points sont au même endroit (adresse
 * commune, position approximative au centre d'une commune) : zoomer ne les séparerait jamais.
 */
export function regionToFit(
  points: readonly { latitude: number; longitude: number }[],
  like: Region,
): Region | null {
  if (points.length === 0) return null;
  const latitudes = points.map((p) => p.latitude);
  const longitudes = points.map((p) => p.longitude);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);
  const minLon = Math.min(...longitudes);
  const maxLon = Math.max(...longitudes);
  const latSpan = maxLat - minLat;
  const lonSpan = maxLon - minLon;
  if (latSpan < SAME_PLACE_SPAN_DEGREES && lonSpan < SAME_PLACE_SPAN_DEGREES) return null;

  const ratio = like.longitudeDelta > 0 ? like.latitudeDelta / like.longitudeDelta : 1;
  // Marge de 40 % : les marqueurs du bord restent visibles et touchables.
  const longitudeDelta = Math.max(lonSpan, latSpan / ratio) * 1.4;
  return {
    latitude: (minLat + maxLat) / 2,
    longitude: (minLon + maxLon) / 2,
    latitudeDelta: longitudeDelta * ratio,
    longitudeDelta,
  };
}
