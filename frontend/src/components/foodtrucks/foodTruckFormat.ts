import type { FoodTruck } from "../explorer/types";

export const SPOT_KIND_LABELS: Record<FoodTruck["spotKind"], string> = {
    MARKET: "Marché",
    PRIVATE: "Espace privé",
    EVENT: "Événement",
    OTHER: "Emplacement",
};

/** Horaires du jour d'une présence (ADR-0013), ou `null` s'ils ne sont pas précisés. */
export function formatHours(foodTruck: Pick<FoodTruck, "startTime" | "endTime">): string | null {
    if (!foodTruck.startTime || !foodTruck.endTime) {
        return null;
    }
    const hour = (value: string) => value.slice(0, 5).replace(":", "h");
    return `${hour(foodTruck.startTime)} – ${hour(foodTruck.endTime)}`;
}

/** Une ligne lisible : type d'emplacement, nom de l'emplacement, horaires. */
export function describePresence(foodTruck: FoodTruck): string {
    return [SPOT_KIND_LABELS[foodTruck.spotKind] ?? null, foodTruck.spotName, formatHours(foodTruck)]
        .filter(Boolean).join(" · ");
}

const DAY_LABELS: Record<string, string> = {
    MO: "Lun", TU: "Mar", WE: "Mer", TH: "Jeu", FR: "Ven", SA: "Sam", SU: "Dim",
};

/** Jours d'une présence (`MO,TU…`), « Tous les jours » sans précision. */
export function describeDays(days: string | null | undefined): string {
    if (!days) {
        return "Tous les jours";
    }
    return days.split(",").map((code) => DAY_LABELS[code] ?? code).join(", ");
}

/** Contact cliquable : une adresse web http(s), sinon `null` (téléphone, réseau social en texte libre). */
export function contactUrl(contact: string | null | undefined): string | null {
    if (!contact) {
        return null;
    }
    try {
        const url = new URL(contact.trim());
        return url.protocol === "https:" || url.protocol === "http:" ? url.toString() : null;
    } catch {
        return null;
    }
}
