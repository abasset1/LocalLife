/**
 * « Ajouter à l'agenda » : URL du formulaire pré-rempli de Google Agenda (même mécanisme que le site
 * web, `frontend/src/utils/googleCalendar.ts`), et texte du partage. Sans dépendance à React Native.
 */

const DEFAULT_DURATION_MS = 2 * 3600_000;

/** Instant → `20260920T190000Z` (format du paramètre `dates`). */
function utc(date: Date): string {
  return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

/** `2026-09-20T19:00:00` (heure locale sans fuseau) → `20260920T190000`, heure « flottante ». */
function floating(local: string): string {
  return local.replace(/[-:]/g, '').replace(/\.\d+$/, '').slice(0, 15);
}

function addHoursLocal(local: string, hours: number): string {
  const date = new Date(local);
  date.setHours(date.getHours() + hours);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export interface CalendarEvent {
  title: string;
  description: string | null;
  location: string | null;
  /** Prochaine occurrence réelle (instant ISO avec fuseau), prioritaire. */
  occurrenceStartAt?: string | null;
  occurrenceEndAt?: string | null;
  /** Repli : date de début de l'activité (heure locale, sans fuseau connu). */
  startDate: string | null;
}

/** URL Google Agenda, ou `null` sans aucune date connue. */
export function buildCalendarUrl(event: CalendarEvent): string | null {
  let dates: string;
  if (event.occurrenceStartAt) {
    const start = new Date(event.occurrenceStartAt);
    const end = event.occurrenceEndAt ? new Date(event.occurrenceEndAt) : new Date(start.getTime() + DEFAULT_DURATION_MS);
    dates = `${utc(start)}/${utc(end)}`;
  } else if (event.startDate) {
    // Date seule (« 2026-10-17 ») : journée entière.
    if (/^\d{4}-\d{2}-\d{2}$/.test(event.startDate)) {
      const next = new Date(`${event.startDate}T12:00:00`);
      next.setDate(next.getDate() + 1);
      const pad = (n: number) => String(n).padStart(2, '0');
      dates = `${event.startDate.replace(/-/g, '')}/${next.getFullYear()}${pad(next.getMonth() + 1)}${pad(next.getDate())}`;
    } else {
      dates = `${floating(event.startDate)}/${floating(addHoursLocal(event.startDate, 2))}`;
    }
  } else {
    return null;
  }
  const params = new URLSearchParams({ action: 'TEMPLATE', text: event.title, dates });
  if (event.location) params.set('location', event.location);
  if (event.description) params.set('details', event.description);
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

/** Texte partagé : titre, puis date et lieu, puis lien de l'organisateur s'il y en a un. */
export function shareMessage(input: {
  title: string;
  date: string | null;
  place: string | null;
  url: string | null;
}): string {
  const details = [input.date, input.place].filter(Boolean).join(' · ');
  return [input.title, details || null, input.url, 'Découvert sur LocalLife'].filter(Boolean).join('\n');
}
