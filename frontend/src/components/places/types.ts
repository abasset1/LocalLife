import type { CoordinatePrecision } from "../../utils/directions";

/** Lieu publié (`PlaceResponse`, backend) : couche « Lieux » de la carte. */
export interface Place {
    id: number;
    name: string;
    placeType: string;
    address: string | null;
    postalCode: string | null;
    city: string | null;
    latitude: number;
    longitude: number;
    description: string | null;
    website: string | null;
    coordinatePrecision: CoordinatePrecision;
}

export type PlaceStatus = "PENDING" | "PUBLISHED" | "REJECTED" | "ARCHIVED";

export type PlaceFlag = "NO_ADDRESS" | "ROUTE_NAME" | "DUPLICATE_NAME" | "APPROXIMATE_POSITION";

/** Lieu de la file de modération (`AdminPlaceResponse`, backend). */
export interface AdminPlace extends Omit<Place, "latitude" | "longitude"> {
    latitude: number | null;
    longitude: number | null;
    status: PlaceStatus;
    flags: PlaceFlag[];
    sourceName: string | null;
    publishedAt: string | null;
    moderatedAt: string | null;
}

export interface AdminPlacePage {
    items: AdminPlace[];
    total: number;
    limit: number;
    offset: number;
}

/** Bilan d'un import ou d'un essai à blanc de lieux (`PlaceImportReport`, backend). */
export interface PlaceImportReport {
    sourceId: number;
    sourceName: string;
    dryRun: boolean;
    verdict: "OK" | "SUSPICIOUS" | "FAILED";
    collected: number;
    whitelisted: number;
    created: number;
    updated: number;
    backToPending: number;
    reappeared: number;
    archived: number;
    archiveWithheld: number;
    rejectedKept: number;
    problems: string[];
}
