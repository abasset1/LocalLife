/**
 * Règles de l'onglet « Liste », sans dépendance à React Native pour rester testables : période de
 * recherche, distance au centre et filtre texte.
 */

export type Period = 'all' | 'today' | 'weekend' | 'week';

export const PERIOD_OPTIONS: readonly { value: Period; label: string }[] = [
  { value: 'all', label: 'Tout' },
  { value: 'today', label: "Aujourd'hui" },
  { value: 'weekend', label: 'Ce week-end' },
  { value: 'week', label: '7 jours' },
];

/** `yyyy-MM-dd` dans le fuseau de l'appareil (celui de l'utilisateur, pas UTC). */
export function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

/**
 * Paramètres `date` / `dateTo` de `GET /activities/nearby` pour une période. Le week-end est celui en
 * cours (samedi ou dimanche : jusqu'à dimanche), sinon le prochain.
 */
export function periodRange(period: Period, now: Date = new Date()): { date?: string; dateTo?: string } {
  switch (period) {
    case 'all':
      return {};
    case 'today':
      return { date: isoDay(now) };
    case 'week':
      return { date: isoDay(now), dateTo: isoDay(addDays(now, 6)) };
    case 'weekend': {
      const weekday = now.getDay(); // 0 = dimanche, 6 = samedi
      if (weekday === 0) return { date: isoDay(now) };
      const saturday = weekday === 6 ? now : addDays(now, 6 - weekday);
      return { date: isoDay(saturday), dateTo: isoDay(addDays(saturday, 1)) };
    }
  }
}

/** Distance à vol d'oiseau en kilomètres (formule de haversine). */
export function distanceKm(
  from: { latitude: number; longitude: number },
  to: { latitude: number; longitude: number },
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(to.latitude - from.latitude);
  const dLon = toRad(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(from.latitude)) * Math.cos(toRad(to.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** « 350 m », « 2,4 km », « 18 km ». */
export function formatDistance(km: number): string {
  if (km < 1) return `${Math.max(10, Math.round((km * 1000) / 10) * 10)} m`;
  if (km < 10) return `${km.toFixed(1).replace('.', ',')} km`;
  return `${Math.round(km)} km`;
}

/** Texte sans casse ni accents, pour une recherche tolérante (« fete » trouve « Fête »). */
export function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

/** Garde les activités dont le titre, la catégorie, la commune ou la description contiennent chaque mot saisi. */
export function matchesQuery(
  activity: { title: string; category: string; city: string | null; description: string | null },
  query: string,
): boolean {
  const words = normalize(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;
  const haystack = normalize(
    [activity.title, activity.category, activity.city, activity.description].filter(Boolean).join(' '),
  );
  return words.every((word) => haystack.includes(word));
}
