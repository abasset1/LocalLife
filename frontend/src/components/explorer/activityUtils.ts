import type { Activity, Category, MapBounds } from "./types";

/**
 * Formate une date en YYYY-MM-DD à partir de ses composants locaux
 * (jour/mois/année) plutôt que via `Date.toISOString()`, qui convertit en
 * UTC et peut décaler la date d'un jour pour les fuseaux horaires en
 * avance sur UTC (ex : Europe/Paris).
 */
export function toLocalDateString(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

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

/**
 * Libellés des catégories répertoriées d'une activité, dans l'ordre de
 * `categoryCodes`. Un code absent du référentiel chargé est ignoré : seules
 * les catégories répertoriées sont affichées, jamais la chaîne libre
 * historique `category`.
 */
export function getCategoryLabels(activity: Activity, categories: Category[]): string[] {
    const labelsByCode = new Map(categories.map((category) => [category.code, category.label]));
    return (activity.categoryCodes ?? [])
        .map((code) => labelsByCode.get(code))
        .filter((label): label is string => Boolean(label));
}

/** Libellés joints pour un affichage sur une ligne (« Culture, Tourisme »), chaîne vide si aucun. */
export function formatCategoryLabels(activity: Activity, categories: Category[]): string {
    return getCategoryLabels(activity, categories).join(", ");
}

/**
 * Filtre texte libre (LL-EF-017, section 7 : "rechercher une activité, un
 * lieu, éventuellement une ville") — appliqué côté client sur les activités
 * déjà chargées, sans appel réseau supplémentaire ni nouvel endpoint
 * backend (ticket typé "Frontend" uniquement, hors périmètre d'ajouter une
 * recherche plein texte côté serveur).
 */
export function matchesSearchQuery(activity: Activity, query: string, categories: Category[] = []): boolean {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
        return true;
    }
    return [activity.title, activity.city, activity.address, ...getCategoryLabels(activity, categories)]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLowerCase().includes(normalized));
}

/** L'activité est-elle dans la zone affichée par la carte (bords compris) ? */
export function isInBounds(activity: Activity, bounds: MapBounds): boolean {
    return (
        activity.latitude >= bounds.swLatitude
        && activity.latitude <= bounds.neLatitude
        && activity.longitude >= bounds.swLongitude
        && activity.longitude <= bounds.neLongitude
    );
}
