import { useEffect, useState } from 'react';

import { listActivitiesAtPlace, type Activity } from '@/api';

/**
 * Activités à venir d'un lieu permanent (ADR-0011, § 7.2.10), chargées à l'ouverture de son aperçu. `null`
 * pendant le chargement ou en cas d'échec : l'aperçu du lieu reste utilisable sans elles.
 */
export function usePlaceActivities(placeId: number): Activity[] | null {
  const [result, setResult] = useState<{ placeId: number; activities: Activity[] } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listActivitiesAtPlace(placeId, { signal: controller.signal })
      .then((activities) => setResult({ placeId, activities }))
      .catch(() => undefined);
    return () => controller.abort();
  }, [placeId]);

  return result && result.placeId === placeId ? result.activities : null;
}
