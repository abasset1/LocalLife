/**
 * Types partagés de l'écran Explorer (LL-EF-017), extraits de App.tsx lors
 * de la refonte pour être réutilisables par les composants dédiés
 * (`ExplorerMap`, `ActivityList`, `ActivityDetailModal`, etc.) — aucun champ
 * ajouté ou retiré par rapport aux interfaces d'origine.
 */

export interface Activity {
    id: number;
    title: string;
    category: string;
    latitude: number;
    longitude: number;
    startDate: string;
    status: string;
    sourceName: string;
    address: string | null;
    city: string | null;
    createdBy: number | null;
    createdByUsername: string | null;
    likedByCurrentUser: boolean;
    mainImageUrl: string | null;
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
}

export interface FoodTruck {
    id: number;
    name: string;
    category: string;
    latitude: number;
    longitude: number;
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
