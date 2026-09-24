/**
 * Mise en forme de la fiche d'une activité (LL-MOB-0008), sans dépendance à
 * React Native pour rester testable.
 */

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

function day(date: Date): string {
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** « 12 octobre 2026 », ou « du 12 au 14 octobre 2026 » (début et fin distincts). */
export function formatDateRange(start: string | null, end?: string | null): string | null {
  const from = parse(start);
  const to = parse(end);
  if (!from && !to) return null;
  if (!from) return `jusqu'au ${day(to!)}`;
  if (!to || day(from) === day(to)) return day(from);
  return `du ${day(from)} au ${day(to)}`;
}

/** « 12 rue X, 13001 Marseille » — les parties absentes sont ignorées. */
export function formatPlace(
  address: string | null,
  postalCode: string | null,
  city: string | null,
): string | null {
  const town = [postalCode, city].filter(Boolean).join(' ');
  const place = [address, town].filter(Boolean).join(', ');
  return place || null;
}

/** N'autorise que http(s) avant d'ouvrir un lien fourni par une source externe. */
export function isSafeWebUrl(url: string | null | undefined): url is string {
  if (!url) return false;
  try {
    const { protocol } = new URL(url);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
}
