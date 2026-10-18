import type { FoodTruckPresence, SpotKind } from '@/api';

export const SPOT_KIND_LABELS: Record<SpotKind, string> = {
  MARKET: 'Marché',
  PRIVATE: 'Espace privé',
  EVENT: 'Événement',
  OTHER: 'Emplacement',
};

const DAY_LABELS: Record<string, string> = {
  MO: 'lun.', TU: 'mar.', WE: 'mer.', TH: 'jeu.', FR: 'ven.', SA: 'sam.', SU: 'dim.',
};

type PresenceLike = Pick<FoodTruckPresence, 'spotName' | 'spotKind' | 'daysOfWeek' | 'startTime' | 'endTime'>;

/** Horaires d'une présence (`08h00 – 13h00`), ou `null` s'ils ne sont pas précisés. */
export function formatHours(presence: Pick<PresenceLike, 'startTime' | 'endTime'>): string | null {
  if (!presence.startTime || !presence.endTime) return null;
  const hour = (value: string) => value.slice(0, 5).replace(':', 'h');
  return `${hour(presence.startTime)} – ${hour(presence.endTime)}`;
}

/** Jours d'une présence (`sam., dim.`), ou `Tous les jours`. */
export function formatDays(daysOfWeek: string | null): string {
  if (!daysOfWeek) return 'Tous les jours';
  return daysOfWeek.split(',').map((day) => DAY_LABELS[day] ?? day).join(', ');
}

/** Une ligne lisible pour la carte : type et nom de l'emplacement, horaires du jour. */
export function describeToday(presence: PresenceLike): string {
  return [SPOT_KIND_LABELS[presence.spotKind], presence.spotName, formatHours(presence)]
    .filter(Boolean)
    .join(' · ');
}

/** Une ligne du planning : jours, horaires, type et nom de l'emplacement. */
export function describeSchedule(presence: PresenceLike): string {
  return [formatDays(presence.daysOfWeek), formatHours(presence), SPOT_KIND_LABELS[presence.spotKind],
    presence.spotName].filter(Boolean).join(' · ');
}
