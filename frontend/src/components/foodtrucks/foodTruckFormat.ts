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

/**
 * Adresse exacte à afficher en plus du nom de l'emplacement : `null` si elle est inconnue ou si elle se confond avec
 * ce nom (un emplacement sans nom prend son adresse comme nom).
 */
export function spotAddressLine(foodTruck: { spotName?: string | null; spotAddress?: string | null }): string | null {
    const address = foodTruck.spotAddress?.trim();
    if (!address) {
        return null;
    }
    return address.toLowerCase() === foodTruck.spotName?.trim().toLowerCase() ? null : address;
}

const SOCIAL_HOSTS = {
    instagram: ["instagram.com", "instagr.am"],
    facebook: ["facebook.com", "fb.com", "fb.me"],
} as const;

/** Lien Instagram ou Facebook affichable : une adresse https du réseau attendu, sinon `null`. */
export function socialUrl(url: string | null | undefined, network: keyof typeof SOCIAL_HOSTS): string | null {
    if (!url) {
        return null;
    }
    try {
        const parsed = new URL(url);
        const host = parsed.hostname.toLowerCase();
        const known = SOCIAL_HOSTS[network].some((domain) => host === domain || host.endsWith(`.${domain}`));
        return parsed.protocol === "https:" && known ? parsed.toString() : null;
    } catch {
        return null;
    }
}
