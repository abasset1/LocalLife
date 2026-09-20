/**
 * LL-EF-013 : construit l'url du formulaire pré-rempli « Ajouter à Google
 * Agenda », sans jamais stocker le moindre identifiant Google côté serveur
 * (critère d'acceptation explicite du ticket) — approche par simple URL de
 * modèle d'événement (`calendar.google.com/calendar/render?action=TEMPLATE`,
 * mécanisme public de Google, sans authentification ni clé API), ouverte
 * dans un nouvel onglet. L'utilisateur reste entièrement libre de modifier
 * chaque champ avant d'enregistrer : c'est Google Agenda lui-même qui
 * affiche le formulaire d'édition, LocalLife ne fait que le pré-remplir.
 */

export interface GoogleCalendarEventInput {
    title: string;
    description: string | null;
    address: string | null;
    city: string | null;
    latitude: number;
    longitude: number;
    /** Instant réel (ISO-8601, avec fuseau) d'une occurrence — voir `OccurrenceSummary.startAt`. */
    occurrenceStartAt: string | null;
    occurrenceEndAt: string | null;
    /** Repli quand aucune occurrence n'est disponible : `Activity.startDate`, heure locale sans fuseau connu. */
    fallbackStartDate: string;
}

/** Durée par défaut d'un événement sans heure de fin connue (ni occurrence, ni schedule). */
const DEFAULT_EVENT_DURATION_HOURS = 2;

/** `"2026-09-20T19:00:00Z"` → `"20260920T190000Z"` (format exigé par le paramètre `dates` de Google Agenda). */
function toGoogleCalendarUtcDateTime(isoInstant: string): string {
    return new Date(isoInstant).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
}

/**
 * `"2026-09-20T19:00:00"` → `"20260920T190000"` (sans suffixe `Z` : heure
 * « flottante », interprétée par Google Agenda dans le fuseau du calendrier
 * de l'utilisateur — `Activity.startDate` n'a pas de fuseau connu côté
 * frontend, contrairement à une occurrence réelle, voir
 * `toGoogleCalendarUtcDateTime` ci-dessus).
 */
function toGoogleCalendarLocalDateTime(localDateTime: string): string {
    return localDateTime.replace(/[-:]/g, "").replace(/\.\d+$/, "").slice(0, 15);
}

function addHours(localDateTime: string, hours: number): string {
    const date = new Date(localDateTime);
    date.setHours(date.getHours() + hours);
    // Reconstruit une chaîne locale "YYYY-MM-DDTHH:mm:ss" (pas toISOString, qui convertirait en UTC).
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T`
        + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function resolveDatesParam(event: GoogleCalendarEventInput): string {
    if (event.occurrenceStartAt) {
        const start = toGoogleCalendarUtcDateTime(event.occurrenceStartAt);
        const end = event.occurrenceEndAt
            ? toGoogleCalendarUtcDateTime(event.occurrenceEndAt)
            : toGoogleCalendarUtcDateTime(
                new Date(new Date(event.occurrenceStartAt).getTime()
                    + DEFAULT_EVENT_DURATION_HOURS * 3600_000).toISOString());
        return `${start}/${end}`;
    }
    const start = event.fallbackStartDate;
    const end = addHours(start, DEFAULT_EVENT_DURATION_HOURS);
    return `${toGoogleCalendarLocalDateTime(start)}/${toGoogleCalendarLocalDateTime(end)}`;
}

function resolveLocationParam(event: GoogleCalendarEventInput): string {
    if (event.address && event.city) {
        return `${event.address}, ${event.city}`;
    }
    if (event.address) {
        return event.address;
    }
    if (event.city) {
        return event.city;
    }
    // Repli sur les coordonnées GPS (critère d'acceptation explicite du ticket) : Google Agenda
    // accepte un texte libre pour "location" et sait généralement géolocaliser "lat,lng".
    return `${event.latitude},${event.longitude}`;
}

export function buildGoogleCalendarUrl(event: GoogleCalendarEventInput): string {
    const params = new URLSearchParams({
        action: "TEMPLATE",
        text: event.title,
        dates: resolveDatesParam(event),
        location: resolveLocationParam(event),
    });
    if (event.description) {
        params.set("details", event.description);
    }
    return `https://calendar.google.com/calendar/render?${params.toString()}`;
}
