import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError, RequestAbortedError, listFoodTrucks, searchWithinBounds } from '@/api';
import type { Activity, FoodTruck } from '@/api';

import { Bounds, Region, boundsContain, isSearchable, regionToBounds } from './geo';

/** Délai sans nouveau mouvement de carte avant de recharger les activités. */
export const REGION_DEBOUNCE_MS = 500;

export type LoadStatus = 'loading' | 'success' | 'error';

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Impossible de charger les données.';
}

/**
 * Charge les données affichées sur la carte (LL-MOB-0005).
 *
 * - Activités : `GET /activities/within-bounds` sur la zone visible,
 *   filtrées par `categoryCodes` (catégories par défaut des paramètres de
 *   recherche ; vide = toutes). Le backend ne renvoie que les activités
 *   `PUBLISHED`.
 * - Food trucks : `GET /foodtrucks` (pas de bounding box dans ce contrat),
 *   chargé une seule fois.
 *
 * Contrôle du nombre d'appels : `region` ne change qu'à la fin d'un geste
 * (`onRegionChangeComplete`, jamais pendant le mouvement du doigt) ; le
 * rechargement est en plus temporisé (`REGION_DEBOUNCE_MS`), la requête
 * précédente est annulée, et aucun appel n'est fait si la zone est déjà
 * entièrement couverte par les données chargées, ni si elle est trop
 * étendue (`tooWide` : la carte invite alors à zoomer).
 */
export function useMapData(region: Region | null, categoryCodes: readonly string[] = []) {
  const [activities, setActivities] = useState<Activity[]>([]);
  const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);
  const [activitiesStatus, setActivitiesStatus] = useState<LoadStatus>('loading');
  const [foodTrucksStatus, setFoodTrucksStatus] = useState<LoadStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  const loadedBounds = useRef<Bounds | null>(null);
  /** Clé stable du filtre : un nouveau tableau aux mêmes codes ne relance rien. */
  const categoryKey = [...categoryCodes].sort().join(',');
  const loadedCategoryKey = useRef(categoryKey);

  // Food trucks : une fois au montage, et à chaque « Réessayer ».
  useEffect(() => {
    const controller = new AbortController();
    setFoodTrucksStatus('loading');
    listFoodTrucks({ signal: controller.signal })
      .then((trucks) => {
        setFoodTrucks(trucks);
        setFoodTrucksStatus('success');
      })
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        setFoodTrucksStatus('error');
        setError(messageFor(e));
      });
    return () => controller.abort();
  }, [retryCount]);

  // Activités de la zone visible.
  useEffect(() => {
    if (!region) return;
    const bounds = regionToBounds(region);
    if (!bounds) return;

    // Zone trop étendue : pas d'appel, les activités déjà chargées restent affichées.
    if (!isSearchable(bounds)) {
      setActivitiesStatus('success');
      return;
    }

    // Zone déjà couverte par le dernier chargement réussi, avec le même filtre : rien à faire.
    if (
      loadedBounds.current &&
      loadedCategoryKey.current === categoryKey &&
      boundsContain(loadedBounds.current, bounds)
    ) {
      // Une requête précédente a pu être annulée par le nettoyage de cet effet.
      setActivitiesStatus('success');
      return;
    }

    const controller = new AbortController();
    setActivitiesStatus('loading');

    const timer = setTimeout(() => {
      searchWithinBounds(
        { ...bounds, category: categoryKey === '' ? undefined : categoryKey },
        { signal: controller.signal },
      )
        .then((result) => {
          loadedBounds.current = bounds;
          loadedCategoryKey.current = categoryKey;
          setActivities(result);
          setActivitiesStatus('success');
        })
        .catch((e: unknown) => {
          if (e instanceof RequestAbortedError) return;
          loadedBounds.current = null;
          setActivitiesStatus('error');
          setError(messageFor(e));
        });
    }, REGION_DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [region, categoryKey, retryCount]);

  const retry = useCallback(() => {
    setError(null);
    loadedBounds.current = null;
    setRetryCount((count) => count + 1);
  }, []);

  const loading = activitiesStatus === 'loading' || foodTrucksStatus === 'loading';
  const failed = activitiesStatus === 'error' || foodTrucksStatus === 'error';
  const bounds = region ? regionToBounds(region) : null;
  const tooWide = bounds !== null && !isSearchable(bounds);

  return { activities, foodTrucks, loading, error: failed ? error : null, tooWide, retry };
}
