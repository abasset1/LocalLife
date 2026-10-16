import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError, RequestAbortedError, listFoodTrucks, searchNearby, searchWithinBounds } from '@/api';
import type { Activity, FoodTruck } from '@/api';

import { periodRange, type Period } from '@/features/list/filters';

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
 * - Activités : `GET /activities/nearby` dans le rayon favori s'il y en a un
 *   (`radiusSearch`), sinon `GET /activities/within-bounds` sur la zone visible,
 *   filtrées par `categoryCodes` (catégories par défaut des paramètres de
 *   recherche ; vide = toutes) et par `period` (période partagée avec l'onglet
 *   « Liste »). Le backend ne renvoie que les activités `PUBLISHED`.
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
/** Rayon favori : seules les activités à moins de `radiusKm` de ce point sont chargées. */
export interface RadiusSearch {
  latitude: number;
  longitude: number;
  radiusKm: number;
}

export function useMapData(
  region: Region | null,
  categoryCodes: readonly string[] = [],
  radiusSearch: RadiusSearch | null = null,
  period: Period = 'all',
) {
  const [activities, setActivities] = useState<Activity[]>([]);
  const [foodTrucks, setFoodTrucks] = useState<FoodTruck[]>([]);
  const [activitiesStatus, setActivitiesStatus] = useState<LoadStatus>('loading');
  const [foodTrucksStatus, setFoodTrucksStatus] = useState<LoadStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  const loadedBounds = useRef<Bounds | null>(null);
  /** Clé stable du filtre : un nouveau tableau aux mêmes codes ne relance rien. */
  const categoryKey = [...categoryCodes].sort().join(',');
  /** Filtres de la dernière zone chargée : catégories et période (la date du jour fait partie de la clé). */
  const filterKey = `${categoryKey}|${period}|${JSON.stringify(periodRange(period))}`;
  const loadedFilterKey = useRef(filterKey);
  const radiusKey = radiusSearch
    ? `${radiusSearch.latitude},${radiusSearch.longitude},${radiusSearch.radiusKm}`
    : null;

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

  // Rayon favori : activités dans le rayon (`/nearby`), indépendamment des
  // déplacements et du zoom de la carte, qui ne font que l'afficher.
  useEffect(() => {
    if (!radiusKey) return;
    const [latitude, longitude, radiusKm] = radiusKey.split(',').map(Number);
    // La zone chargée ne correspond plus à une recherche par zone visible.
    loadedBounds.current = null;
    const controller = new AbortController();
    setActivitiesStatus('loading');
    searchNearby(
      {
        latitude,
        longitude,
        radiusKm,
        category: categoryKey === '' ? undefined : categoryKey,
        ...periodRange(period),
      },
      { signal: controller.signal },
    )
      .then((result) => {
        setActivities(result);
        setActivitiesStatus('success');
      })
      .catch((e: unknown) => {
        if (e instanceof RequestAbortedError) return;
        setActivitiesStatus('error');
        setError(messageFor(e));
      });
    return () => controller.abort();
  }, [radiusKey, categoryKey, period, retryCount]);

  // Activités de la zone visible (sans rayon favori).
  useEffect(() => {
    if (!region || radiusKey) return;
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
      loadedFilterKey.current === filterKey &&
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
        { ...bounds, category: categoryKey === '' ? undefined : categoryKey, ...periodRange(period) },
        { signal: controller.signal },
      )
        .then((result) => {
          loadedBounds.current = bounds;
          loadedFilterKey.current = filterKey;
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
  }, [region, categoryKey, filterKey, period, radiusKey, retryCount]);

  const retry = useCallback(() => {
    setError(null);
    loadedBounds.current = null;
    setRetryCount((count) => count + 1);
  }, []);

  const loading = activitiesStatus === 'loading' || foodTrucksStatus === 'loading';
  const failed = activitiesStatus === 'error' || foodTrucksStatus === 'error';
  const bounds = region ? regionToBounds(region) : null;
  // Avec un rayon favori, la zone visible ne limite pas la recherche.
  const tooWide = radiusKey === null && bounds !== null && !isSearchable(bounds);

  return { activities, foodTrucks, loading, error: failed ? error : null, tooWide, retry };
}
