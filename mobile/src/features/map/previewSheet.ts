import { distanceKm, formatDistance } from '@/features/list/filters';

/**
 * Règles du panneau d'aperçu de la carte (issue #226), sans dépendance à React Native pour rester testables :
 * geste sur la poignée, date courte, distance.
 */

/** Distance (en points d'écran) à partir de laquelle un glissement de la poignée compte. */
export const SWIPE_THRESHOLD = 30;

export type SheetMove = 'expand' | 'collapse' | 'close';

/**
 * Glissement vertical de la poignée (`dy` > 0 vers le bas) : vers le haut, le panneau réduit se développe ; vers le
 * bas, le panneau développé se réduit et le panneau réduit se ferme. `null` : geste trop court ou sans effet.
 */
export function sheetMoveFor(dy: number, expanded: boolean): SheetMove | null {
  if (dy <= -SWIPE_THRESHOLD) return expanded ? null : 'expand';
  if (dy >= SWIPE_THRESHOLD) return expanded ? 'collapse' : 'close';
  return null;
}

/** « 8 h », « 18 h 30 ». */
function hour(date: Date): string {
  const minutes = date.getMinutes();
  return `${date.getHours()} h${minutes ? ` ${String(minutes).padStart(2, '0')}` : ''}`;
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** L'heure est connue (« 2026-11-14T18:30:00 »), et ce n'est pas un minuit de remplissage. */
function hasTime(iso: string, date: Date): boolean {
  return iso.length > 10 && (date.getHours() !== 0 || date.getMinutes() !== 0);
}

/**
 * Date courte de l'aperçu : « Aujourd’hui · 8 h – 13 h », « Demain · 21 h », « sam. 14 nov. · 18 h 30 »,
 * « sam. 14 nov. » sans heure connue. `null` sans date lisible.
 */
export function formatWhen(start: string | null, end: string | null, now: Date = new Date()): string | null {
  if (!start) return null;
  const from = new Date(start);
  if (Number.isNaN(from.getTime())) return null;
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  const day = sameDay(from, now)
    ? 'Aujourd’hui'
    : sameDay(from, tomorrow)
      ? 'Demain'
      : from.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
  if (!hasTime(start, from)) return day;
  const to = end ? new Date(end) : null;
  const until = to && !Number.isNaN(to.getTime()) && hasTime(end as string, to) && to > from && to.getTime() - from.getTime() < 24 * 3600_000
    ? ` – ${hour(to)}`
    : '';
  return `${day} · ${hour(from)}${until}`;
}

/** « Avignon · 0,5 km » : la distance n'est ajoutée que si la position de l'utilisateur est connue. */
export function placeWithDistance(
  place: string | null,
  target: { latitude: number; longitude: number },
  origin: { latitude: number; longitude: number } | null,
): string | null {
  const distance = origin ? formatDistance(distanceKm(origin, target)) : null;
  const text = [place, distance].filter(Boolean).join(' · ');
  return text === '' ? null : text;
}
