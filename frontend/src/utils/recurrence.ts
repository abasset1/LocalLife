/**
 * LL-11015, critère d'acceptation implicite (« et non afficher directement :
 * FREQ=WEEKLY;BYDAY=TU ») : traduit une règle de récurrence RFC 5545 (RRULE,
 * voir `RecurrenceRuleService` côté backend, LL-11003) en une phrase lisible
 * en français, jamais la chaîne brute.
 *
 * Volontairement limité à ce que le backend produit réellement aujourd'hui
 * (LL-11003 : `FREQ=WEEKLY;BYDAY=...`, un ou plusieurs jours) — les autres
 * fréquences RFC 5545 (`DAILY`/`MONTHLY`/`YEARLY`, etc.) ne sont émises par
 * aucun collecteur actuel (voir `NormalizationService`/`OpenAgendaCollector`
 * côté backend, qui ne renseignent jamais `recurrenceRule`) ; un repli
 * générique (mais toujours en français, jamais la chaîne brute) couvre ce
 * cas si un jour une source en fournissait une.
 */

const WEEKDAY_LABELS: Record<string, string> = {
    MO: "lundi",
    TU: "mardi",
    WE: "mercredi",
    TH: "jeudi",
    FR: "vendredi",
    SA: "samedi",
    SU: "dimanche",
};

/** `["MO", "WE"]` → `"lundis et mercredis"` (au pluriel, jamais un seul jour isolé sans "Tous les/Tous les"). */
function formatWeekdayList(days: string[]): string {
    const labels = days.map((day) => `${WEEKDAY_LABELS[day] ?? day.toLowerCase()}s`);
    if (labels.length === 1) {
        return labels[0];
    }
    return `${labels.slice(0, -1).join(", ")} et ${labels[labels.length - 1]}`;
}

/**
 * Parse une valeur RRULE minimale (`CLE=VALEUR;CLE=VALEUR`, comme émise par
 * `RecurrenceRuleService`) en une {@link Map}, sans dépendance externe —
 * suffisant pour le sous-ensemble réellement produit (voir la javadoc de ce
 * fichier), pas un parseur RFC 5545 complet.
 */
function parseRecurrenceRule(recurrenceRule: string): Map<string, string> {
    const entries = recurrenceRule.split(";").map((part) => part.split("=") as [string, string]);
    return new Map(entries.filter(([key, value]) => key && value));
}

/**
 * Formate une règle de récurrence en français ("Tous les mardis"), ou
 * `null` si `recurrenceRule` est `null`/vide (activité ponctuelle, sans
 * récurrence à afficher — voir `ScheduleSummary.recurrenceRule` côté
 * backend, nullable).
 */
export function formatRecurrenceRule(recurrenceRule: string | null): string | null {
    if (!recurrenceRule) {
        return null;
    }
    const parts = parseRecurrenceRule(recurrenceRule);
    const frequency = parts.get("FREQ");
    const byDay = parts.get("BYDAY");

    if (frequency === "WEEKLY" && byDay) {
        const days = byDay.split(",").filter((day) => day in WEEKDAY_LABELS);
        if (days.length > 0) {
            return `Tous les ${formatWeekdayList(days)}`;
        }
    }
    if (frequency === "DAILY") {
        return "Tous les jours";
    }
    if (frequency === "MONTHLY") {
        return "Tous les mois";
    }
    // Repli générique (fréquence non couverte ci-dessus) : toujours une phrase en
    // français, jamais la chaîne RRULE brute (critère d'acceptation du ticket).
    return "Se répète régulièrement";
}

/** `"11:30:00"` → `"11h30"` (format d'affichage français, secondes ignorées). */
export function formatTimeOfDay(time: string | null): string | null {
    if (!time) {
        return null;
    }
    const [hours, minutes] = time.split(":");
    return `${hours}h${minutes}`;
}

/** `"11:30:00"`, `"14:00:00"` → `"11h30 → 14h00"` — un seul horaire si `endTime` est absent. */
export function formatTimeRange(startTime: string | null, endTime: string | null): string | null {
    const start = formatTimeOfDay(startTime);
    const end = formatTimeOfDay(endTime);
    if (!start) {
        return null;
    }
    return end ? `${start} → ${end}` : start;
}
