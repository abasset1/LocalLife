import { useEffect, useRef, useState } from 'react';

import { RequestAbortedError, searchPlacesWithinBounds, type Place } from '@/api';

import { PLACES_MAX_SPAN_DEGREES, boundsContain, isSearchable, regionToBounds, type Bounds, type Region } from './geo';

/** Même délai que les activités : pas de requête pendant un déplacement. */
const REGION_DEBOUNCE_MS = 500;

/**
 * Couche « Lieux » (ADR-0011, phase B) : lieux permanents publiés de la zone
 * visible, chargés seulement quand la couche est activée. Un échec laisse la
 * carte utilisable sans lieux (aucune erreur affichée : couche secondaire).
 */
export function usePlaces(region: Region | null, enabled: boolean): Place[] {
  const [places, setPlaces] = useState<Place[]>([]);
  const loadedBounds = useRef<Bounds | null>(null);

  useEffect(() => {
    if (!enabled || !region) return;
    const bounds = regionToBounds(region);
    if (!bounds || !isSearchable(bounds, PLACES_MAX_SPAN_DEGREES)) return;
    if (loadedBounds.current && boundsContain(loadedBounds.current, bounds)) return;

    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchPlacesWithinBounds(bounds, { signal: controller.signal })
        .then((result) => {
          loadedBounds.current = bounds;
          setPlaces(result);
        })
        .catch((e: unknown) => {
          if (e instanceof RequestAbortedError) return;
          loadedBounds.current = null;
        });
    }, REGION_DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [region, enabled]);

  return enabled ? places : [];
}
