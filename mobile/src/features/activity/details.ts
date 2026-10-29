/**
 * Mise en forme des informations détaillées d'une fiche (horaires, dates, âge, accessibilité, liens),
 * sans dépendance à React Native pour rester testable. Reprend les règles du site web
 * (`frontend/src/utils/recurrence.ts`).
 */
import type { AccessibilitySummary, ActivityDetail, LinkSummary, OccurrenceSummary, ScheduleSummary } from '@/api';

import { formatDateRange } from './format';

const WEEKDAYS: Record<string, string> = {
  MO: 'lundi',
  TU: 'mardi',
  WE: 'mercredi',
  TH: 'jeudi',
  FR: 'vendredi',
  SA: 'samedi',
  SU: 'dimanche',
};

/** `FREQ=WEEKLY;BYDAY=TU,TH` → « Tous les mardis et jeudis » ; jamais la règle brute. */
export function formatRecurrence(rule: string | null): string | null {
  if (!rule) return null;
  const parts = new Map(
    rule
      .split(';')
      .map((part) => part.split('=') as [string, string])
      .filter(([key, value]) => key && value),
  );
  const frequency = parts.get('FREQ');
  const days = (parts.get('BYDAY') ?? '').split(',').filter((day) => day in WEEKDAYS);
  if (frequency === 'WEEKLY' && days.length > 0) {
    const labels = days.map((day) => `${WEEKDAYS[day]}s`);
    const list = labels.length === 1 ? labels[0] : `${labels.slice(0, -1).join(', ')} et ${labels.at(-1)}`;
    return `Tous les ${list}`;
  }
  if (frequency === 'DAILY') return 'Tous les jours';
  if (frequency === 'MONTHLY') return 'Tous les mois';
  return 'Se répète régulièrement';
}

/** `"11:30:00"` → « 11h30 ». */
export function formatTime(time: string | null): string | null {
  if (!time) return null;
  const [hours, minutes] = time.split(':');
  return minutes ? `${hours}h${minutes}` : null;
}

/** « 11h30 – 14h00 », ou l'heure de début seule. */
export function formatTimeRange(start: string | null, end: string | null): string | null {
  const from = formatTime(start);
  if (!from) return null;
  const to = formatTime(end);
  return to ? `${from} – ${to}` : from;
}

function shortDay(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' });
}

/** Une ligne par horaire régulier : « Tous les mardis · 11h30 – 14h00 · jusqu'au 30 novembre ». */
export function formatSchedule(schedule: ScheduleSummary): string | null {
  const parts = [
    formatRecurrence(schedule.recurrenceRule),
    formatTimeRange(schedule.startTime, schedule.endTime),
    schedule.validUntil ? `jusqu'au ${shortDay(schedule.validUntil)}` : null,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : null;
}

/** Les prochaines dates (non annulées, pas encore terminées), dans l'ordre chronologique. */
export function upcomingOccurrences(occurrences: readonly OccurrenceSummary[], now: Date = new Date()) {
  return occurrences
    .filter((o) => o.status !== 'CANCELLED')
    .filter((o) => new Date(o.endAt ?? o.startAt).getTime() >= now.getTime())
    .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
}

/**
 * Date en tête de fiche : la prochaine occurrence, sinon la période de l'activité. La période seule
 * trompe pour des dates séparées (vide-grenier le 19 avril et le 1er novembre : « du 19 avril au
 * 1er novembre »).
 */
export function formatDetailDate(
  detail: Pick<ActivityDetail, 'occurrences' | 'startDate' | 'endDate'>,
  now: Date = new Date(),
): string | null {
  const next = upcomingOccurrences(detail.occurrences, now)[0];
  return next ? formatDateRange(next.startAt, next.endAt) : formatDateRange(detail.startDate, detail.endDate);
}

/** « samedi 17 octobre · 10h00 – 12h00 ». */
export function formatOccurrence(occurrence: OccurrenceSummary): string {
  const start = new Date(occurrence.startAt);
  const day = start.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  const time = (date: Date) => `${String(date.getHours()).padStart(2, '0')}h${String(date.getMinutes()).padStart(2, '0')}`;
  const end = occurrence.endAt ? new Date(occurrence.endAt) : null;
  const sameDay = end && end.toDateString() === start.toDateString();
  return `${day} · ${time(start)}${end && sameDay ? ` – ${time(end)}` : ''}`;
}

/** « Dès 6 ans », « De 6 à 12 ans », « Jusqu'à 12 ans », ou `null` (tout public). */
export function formatAgeRange(ageMin: number | null, ageMax: number | null): string | null {
  if (ageMin !== null && ageMax !== null) return ageMin === ageMax ? `${ageMin} ans` : `De ${ageMin} à ${ageMax} ans`;
  if (ageMin !== null && ageMin > 0) return `Dès ${ageMin} ans`;
  if (ageMax !== null) return `Jusqu'à ${ageMax} ans`;
  return null;
}

const ACCESSIBILITY_LABELS: Record<keyof AccessibilitySummary, string> = {
  motorImpairment: 'Handicap moteur',
  hearingImpairment: 'Handicap auditif',
  visualImpairment: 'Handicap visuel',
  psychicImpairment: 'Handicap psychique',
  intellectualImpairment: 'Handicap mental',
};

/** Handicaps pour lesquels l'activité est déclarée accessible. */
export function accessibilityLabels(accessibility: AccessibilitySummary | null): string[] {
  if (!accessibility) return [];
  return (Object.keys(ACCESSIBILITY_LABELS) as (keyof AccessibilitySummary)[])
    .filter((key) => accessibility[key] === true)
    .map((key) => ACCESSIBILITY_LABELS[key]);
}

export type LinkAction = { label: string; url: string; kind: 'web' | 'phone' | 'email' };

/** Lien de contact ou de billetterie à ouvrir ; `null` pour un type inconnu ou une valeur non sûre. */
export function linkAction(link: LinkSummary): LinkAction | null {
  const value = link.value.trim();
  switch (link.type) {
    case 'PHONE': {
      const digits = value.replace(/[^\d+]/g, '');
      return digits.length >= 6 ? { kind: 'phone', label: link.label || `Appeler le ${value}`, url: `tel:${digits}` } : null;
    }
    case 'EMAIL':
      return /^[^\s@]+@[^\s@]+$/.test(value)
        ? { kind: 'email', label: link.label || 'Envoyer un e-mail', url: `mailto:${value}` }
        : null;
    default:
      return /^https?:\/\//i.test(value) ? { kind: 'web', label: link.label || 'Site web', url: value } : null;
  }
}
