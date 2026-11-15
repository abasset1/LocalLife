/**
 * Dates et heures du formulaire « Proposer une activité » : combinées en `yyyy-MM-ddTHH:mm` pour le backend
 * (qui accepte une date seule ou une date-heure, heure locale du lieu). Mêmes règles que l'application mobile
 * (`mobile/src/features/contribution/form.ts`).
 */

export type DateTimeResult =
    | { ok: true; startDate?: string; endDate?: string }
    | { ok: false; error: string };

function nextDay(date: string): string {
    const [year, month, day] = date.split("-").map(Number);
    const next = new Date(Date.UTC(year, month - 1, day + 1));
    return next.toISOString().slice(0, 10);
}

function join(date: string, time: string): string {
    return time ? `${date}T${time}` : date;
}

/**
 * Début et fin envoyés au backend. Sans date de fin, la fin est le jour du début ; une heure de fin antérieure à
 * l'heure de début désigne alors le lendemain (soirée de 22 h à 2 h).
 */
export function combineDateTimes(
    startDate: string, startTime: string, endDate: string, endTime: string,
): DateTimeResult {
    const start = startDate.trim();
    const end = endDate.trim();
    const startClock = startTime.trim();
    const endClock = endTime.trim();
    if (!start && (startClock || (endClock && !end))) {
        return { ok: false, error: "Indique la date de début pour préciser l'heure." };
    }
    if (!start) {
        return { ok: true, endDate: end ? join(end, endClock) : undefined };
    }
    let resolvedEnd: string | undefined;
    if (end) {
        resolvedEnd = join(end, endClock);
    } else if (endClock) {
        resolvedEnd = join(startClock && endClock <= startClock ? nextDay(start) : start, endClock);
    }
    const resolvedStart = join(start, startClock);
    if (resolvedEnd && end) {
        // Comparaison de chaînes ISO (même format) : une date seule vaut minuit.
        const startKey = startClock ? resolvedStart : `${start}T00:00`;
        const endKey = endClock ? resolvedEnd : `${end}T23:59`;
        if (endKey < startKey) {
            return { ok: false, error: "La fin de l'activité doit être après son début." };
        }
    }
    return { ok: true, startDate: resolvedStart, endDate: resolvedEnd };
}

/** `2030-06-21T20:30:00` → date `2030-06-21` et heure `20:30` ; minuit (date sans heure) → heure vide. */
export function splitDateTime(value: string | null | undefined): { date: string; time: string } {
    if (!value) {
        return { date: "", time: "" };
    }
    const date = value.slice(0, 10);
    const time = value.length >= 16 ? value.slice(11, 16) : "";
    return { date, time: time === "00:00" ? "" : time };
}
