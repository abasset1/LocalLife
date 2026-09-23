import type { Activity } from "./types";

/** Repli affiché quand une activité n'a pas de ville résolue (LL-EF-008). */
export const UNKNOWN_CITY_LABEL = "Ville non renseignée";

/** Repli affiché quand une activité n'a pas d'adresse résolue (LL-10007). */
export const UNKNOWN_ADDRESS_LABEL = "Adresse non renseignée";

/**
 * Lieu affiché à l'utilisateur (LL-EF-008, demande d'Alex : des
 * coordonnées GPS brutes ne sont pas viables). Ordre de repli :
 * `address` (la plus précise et la plus lisible) puis `city` seule, puis
 * enfin les coordonnées.
 */
export function formatActivityLocation(activity: Activity): string {
    if (activity.address) {
        return activity.address;
    }
    if (activity.city) {
        return activity.city;
    }
    return `${activity.latitude.toFixed(4)}, ${activity.longitude.toFixed(4)}`;
}

function compareCities(cityA: string, cityB: string): number {
    if (cityA === UNKNOWN_CITY_LABEL) {
        return cityB === UNKNOWN_CITY_LABEL ? 0 : 1;
    }
    if (cityB === UNKNOWN_CITY_LABEL) {
        return -1;
    }
    return cityA.localeCompare(cityB, "fr");
}

/**
 * Regroupe les activités par ville puis trie chaque groupe par date
 * (LL-EF-008). Les groupes sont triés alphabétiquement, les activités sans
 * ville connue regroupées sous `UNKNOWN_CITY_LABEL`, toujours en dernier.
 */
export function groupActivitiesByCity(items: Activity[]): Array<[string, Activity[]]> {
    const groups = new Map<string, Activity[]>();
    for (const activity of items) {
        const city = activity.city ?? UNKNOWN_CITY_LABEL;
        const group = groups.get(city);
        if (group) {
            group.push(activity);
        } else {
            groups.set(city, [activity]);
        }
    }

    const sortByDate = (a: Activity, b: Activity) => a.startDate.localeCompare(b.startDate);
    for (const group of groups.values()) {
        group.sort(sortByDate);
    }

    return Array.from(groups.entries()).sort(([cityA], [cityB]) => compareCities(cityA, cityB));
}

export function buildCategoryOptions(items: Activity[]): string[] {
    const categories = items.map((item) => item.category).filter((category): category is string => Boolean(category));
    return Array.from(new Set(categories)).sort((a, b) => a.localeCompare(b, "fr"));
}

/**
 * Filtre texte libre (LL-EF-017, section 7 : "rechercher une activité, un
 * lieu, éventuellement une ville") — appliqué côté client sur les activités
 * déjà chargées, sans appel réseau supplémentaire ni nouvel endpoint
 * backend (ticket typé "Frontend" uniquement, hors périmètre d'ajouter une
 * recherche plein texte côté serveur).
 */
export function matchesSearchQuery(activity: Activity, query: string): boolean {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
        return true;
    }
    return [activity.title, activity.city, activity.address, activity.category]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLowerCase().includes(normalized));
}
