import type { CoordinatePrecision } from '@/api';

/** Mention d'une position qui désigne une zone et non le lieu (ADR-0011, phase A), comme sur le site web. */
export const APPROXIMATE_LOCATION_LABEL = 'Emplacement approximatif';

export function isApproximateLocation(item: { coordinatePrecision?: CoordinatePrecision | null }): boolean {
  return item.coordinatePrecision === 'APPROXIMATE';
}

/** Lieu affiché, suivi de la mention si la position est approximative. */
export function withApproximateMention(
  place: string | null,
  item: { coordinatePrecision?: CoordinatePrecision | null },
): string | null {
  if (!isApproximateLocation(item)) return place;
  const mention = APPROXIMATE_LOCATION_LABEL.toLowerCase();
  return place ? `${place} (${mention})` : APPROXIMATE_LOCATION_LABEL;
}

export interface DirectionsDestination {
  latitude: number | null;
  longitude: number | null;
  address: string | null;
  city: string | null;
  coordinatePrecision?: CoordinatePrecision | null;
}

/**
 * Itinéraire : url universelle Google Maps (sans clé), qui ouvre l'application
 * de navigation de l'appareil — même règle que le site web
 * (`frontend/src/utils/directions.ts`). Coordonnées d'abord ; une position
 * approximative vise l'adresse ou la commune en texte, pas le point. `null`
 * s'il n'y a aucune destination.
 */
export function buildDirectionsUrl(destination: DirectionsDestination): string | null {
  const text = [destination.address, destination.city].filter(Boolean).join(', ');
  const approximate = isApproximateLocation(destination) && text !== '';
  const hasCoordinates =
    !approximate &&
    destination.latitude !== null &&
    destination.longitude !== null &&
    Number.isFinite(destination.latitude) &&
    Number.isFinite(destination.longitude);
  const target = hasCoordinates ? `${destination.latitude},${destination.longitude}` : text;
  if (target === '') return null;
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(target)}`;
}
