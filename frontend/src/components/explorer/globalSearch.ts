import { NO_DATE_FILTER } from "./constants";
import type { Activity, UserPosition } from "./types";

/** Résultats par page de la recherche globale (contrat #91 : 1 à 100). */
export const SEARCH_PAGE_SIZE = 20;

/** Libellé d'un résultat sans lieu effectif connu (contrat #91 : champs de lieu `null`). */
export const UNKNOWN_PLACE_LABEL = "Lieu non précisé";

/** Élément de `GET /api/v1/activities/search` (voir docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md). */
export interface GlobalSearchItem {
    id: number;
    title: string;
    description: string | null;
    category: string | null;
    categoryCodes: string[] | null;
    city: string | null;
    address: string | null;
    latitude: number | null;
    longitude: number | null;
    startDate: string | null;
    endDate: string | null;
    relevance: string;
    distanceMeters: number | null;
}

export interface GlobalSearchPage {
    items: GlobalSearchItem[];
    limit: number;
    offset: number;
    total: number;
}

/** Recherche demandée : texte envoyé tel que saisi, filtres actifs, position de référence si elle existe déjà. */
export interface GlobalSearchRequest {
    query: string;
    /** Codes de catégorie triés, séparés par des virgules ; vide = pas de filtre. */
    categoriesKey: string;
    date: string;
    dateTo: string;
    /** Position déjà connue (géolocalisation, ville par défaut, ville choisie) ; jamais déduite du texte. */
    position: UserPosition | null;
    offset: number;
    limit?: number;
}

/** URL de la recherche globale. Aucun rayon ni zone visible : les résultats ne sont pas limités à la carte. */
export function buildSearchUrl(request: GlobalSearchRequest): string {
    const params = new URLSearchParams();
    params.set("q", request.query.trim());
    if (request.position) {
        params.set("latitude", String(request.position.latitude));
        params.set("longitude", String(request.position.longitude));
    }
    if (request.categoriesKey) {
        params.set("category", request.categoriesKey);
    }
    if (request.date !== NO_DATE_FILTER) {
        params.set("date", request.date);
        if (request.dateTo !== NO_DATE_FILTER) {
            params.set("dateTo", request.dateTo);
        }
    }
    params.set("limit", String(request.limit ?? SEARCH_PAGE_SIZE));
    params.set("offset", String(request.offset));
    return `/api/v1/activities/search?${params.toString()}`;
}

/** Le résultat a-t-il une position affichable sur la carte ? */
export function hasCoordinates(activity: Pick<Activity, "latitude" | "longitude">): boolean {
    return Number.isFinite(activity.latitude) && Number.isFinite(activity.longitude);
}

/**
 * Convertit un résultat en `Activity` pour la liste, la carte, l'aperçu et la fiche. La recherche ne
 * renvoie ni statut, ni source, ni auteur, ni image : ils sont repris de `known` (la même activité déjà
 * chargée par la carte) quand elle existe. Sans lieu, les coordonnées valent `NaN` : la carte ne
 * l'affiche pas, la liste montre `UNKNOWN_PLACE_LABEL`. Sans date, `startDate` est vide.
 */
export function toActivity(item: GlobalSearchItem, liked: boolean | undefined, known?: Activity): Activity {
    return {
        status: "PUBLISHED",
        sourceName: "",
        createdBy: null,
        createdByUsername: null,
        mainImageUrl: null,
        ...known,
        id: item.id,
        title: item.title,
        category: item.category ?? "",
        categoryCodes: item.categoryCodes ?? [],
        city: item.city,
        address: item.address,
        latitude: item.latitude ?? Number.NaN,
        longitude: item.longitude ?? Number.NaN,
        startDate: item.startDate ?? "",
        likedByCurrentUser: liked ?? known?.likedByCurrentUser ?? false,
        distanceMeters: item.distanceMeters,
    };
}

/** « à 850 m », « à 1,2 km », « à 35 km » ; `null` sans distance connue. */
export function formatDistance(distanceMeters: number | null | undefined): string | null {
    if (distanceMeters === null || distanceMeters === undefined || !Number.isFinite(distanceMeters)) {
        return null;
    }
    if (distanceMeters < 1000) {
        return `à ${Math.round(distanceMeters)} m`;
    }
    const km = distanceMeters / 1000;
    return `à ${km.toLocaleString("fr-FR", { maximumFractionDigits: km < 10 ? 1 : 0 })} km`;
}
