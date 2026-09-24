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
