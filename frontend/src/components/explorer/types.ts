import type { CoordinatePrecision } from "../../utils/directions";

/**
 * Types partagés de l'écran Explorer (LL-EF-017), extraits de App.tsx lors
 * de la refonte pour être réutilisables par les composants dédiés
 * (`ExplorerMap`, `ActivityList`, `ActivityDetailModal`, etc.) — aucun champ
 * ajouté ou retiré par rapport aux interfaces d'origine.
 */

/** Catégorie du référentiel (`GET /api/v1/categories`). */
export interface Category {
    code: string;
    label: string;
    description: string | null;
    active: boolean;
}

/** Miroir de `UserPreferencesResponse` (`GET`/`PUT /api/v1/users/me/preferences`) : paramètres de recherche par défaut. */
export interface UserPreferences {
    defaultCity: string | null;
    defaultLatitude: number | null;
    defaultLongitude: number | null;
    defaultRadiusKm: number | null;
    defaultCategoryCodes: string[];
    /** `false` tant que l'utilisateur n'a jamais enregistré ses préférences (accueil de première connexion). */
    configured?: boolean;
}

export interface Activity {
    id: number;
    title: string;
    /** Chaîne libre historique (premier tag de la source) : n'est plus affichée ni utilisée comme filtre. */
    category: string;
    /** Codes des catégories du référentiel de l'activité (1..N). */
    categoryCodes?: string[];
    latitude: number;
    longitude: number;
    startDate: string;
    status: string;
    /** Date du premier passage au statut publié ; absente pour les activités publiées avant son suivi. */
    publishedAt?: string | null;
    sourceName: string;
    address: string | null;
    city: string | null;
    createdBy: number | null;
    createdByUsername: string | null;
    likedByCurrentUser: boolean;
    mainImageUrl: string | null;
    /** Recherche globale (#92) : distance au lieu retenu, `null` sans position de référence ou sans lieu. */
    distanceMeters?: number | null;
    /**
     * ADR-0011, phase A : précision de la position. Seule `APPROXIMATE` change l'affichage (mention,
     * marqueur atténué, itinéraire vers la commune). Absente (anciennes réponses) : traitée comme `UNKNOWN`.
     */
    coordinatePrecision?: CoordinatePrecision | null;
    /** Lieu géré par l'auteur qui propose l'activité (« Proposé par … ») ; `null` dès qu'il ne le gère plus. */
    hostPlace?: HostPlace | null;
}

/** Lieu hôte d'une activité (`hostPlace` des réponses activité, backend). */
export interface HostPlace {
    id: number;
    name: string;
}

export interface ScheduleSummary {
    id: number;
    locationId: number | null;
    startTime: string | null;
    endTime: string | null;
    recurrenceRule: string | null;
}

export interface OccurrenceSummary {
    id: number;
    scheduleId: number;
    startAt: string;
    endAt: string | null;
    locationId: number | null;
    status: string;
}

export interface LocationSummary {
    id: number;
    name: string | null;
    address: string | null;
    city: string | null;
    postalCode: string | null;
}

export interface MediaSummary {
    id: number;
    url: string;
    altText: string | null;
    credit: string | null;
}

export interface LinkSummary {
    type: string;
    label: string | null;
    value: string;
}

export interface AccessibilitySummary {
    motorImpairment: boolean | null;
    hearingImpairment: boolean | null;
    visualImpairment: boolean | null;
    psychicImpairment: boolean | null;
    intellectualImpairment: boolean | null;
}

export interface ActivityDetail {
    id: number;
    description: string | null;
    longDescription: string | null;
    conditions: string | null;
    ageMin: number | null;
    ageMax: number | null;
    schedules: ScheduleSummary[];
    occurrences: OccurrenceSummary[];
    locations: LocationSummary[];
    media: MediaSummary[];
    links: LinkSummary[];
    accessibility: AccessibilitySummary | null;
    tags: string[];
    additionalInfo: string | null;
    /** Lieu géré par l'auteur qui propose l'activité, sinon `null`. */
    hostPlace?: HostPlace | null;
}

/**
 * Food truck présent aujourd'hui à un emplacement (`FoodTruckMapResponse`, backend — ADR-0013) : un élément par
 * présence ; `id` est celui du food truck, `latitude`/`longitude` celles de l'emplacement.
 */
export interface FoodTruck {
    id: number;
    name: string;
    category: string;
    latitude: number;
    longitude: number;
    presenceId: number;
    spotName: string | null;
    spotKind: "MARKET" | "PRIVATE" | "EVENT" | "OTHER";
    startTime: string | null;
    endTime: string | null;
    /** Lieu LocalLife où se trouve l'emplacement, ou `null`. */
    placeId: number | null;
}

export interface ApiErrorBody {
    message: string;
}

export interface UserPosition {
    latitude: number;
    longitude: number;
}

export interface MapBounds {
    swLatitude: number;
    swLongitude: number;
    neLatitude: number;
    neLongitude: number;
}

/** Miroir minimal de `JwtPayload` (authStorage.ts, non exporté) — utilisateur affiché dans le menu Explorer. */
export interface ExplorerCurrentUser {
    userId: number;
    email: string;
    role: string;
}

/**
 * Contribution vue par son auteur (`ContributionResponse` côté backend) : « Mes contributions » et
 * pré-remplissage du formulaire de modification.
 */
export interface Contribution {
    id: number;
    title: string;
    description: string | null;
    /** `PENDING` (en attente de validation), `PUBLISHED`, `REJECTED`, `CANCELLED`, `ARCHIVED`. */
    status: string;
    address: string | null;
    city: string | null;
    startDate: string | null;
    endDate: string | null;
    categoryCodes: string[];
    url: string | null;
    phone: string | null;
    email: string | null;
    recurrence: {
        daysOfWeek: string[];
        startTime: string;
        endTime: string | null;
        from: string;
        until: string | null;
    } | null;
    /** Lieu publié géré par l'auteur, qui propose l'activité (« Proposé par … »), sinon `null`. */
    hostPlaceId?: number | null;
}
