import type { Place, PlaceStatus } from "../places/types";

/** Statut d'une demande de gestion (`ClaimStatus`, backend — ADR-0012). */
export type ClaimStatus = "DRAFT" | "SUBMITTED" | "IN_REVIEW" | "INFO_REQUESTED" | "APPROVED" | "REJECTED" | "WITHDRAWN";

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
    DRAFT: "Brouillon",
    SUBMITTED: "Envoyée",
    IN_REVIEW: "En examen",
    INFO_REQUESTED: "Informations demandées",
    APPROVED: "Approuvée",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
};

export const PLACE_STATUS_LABELS: Record<PlaceStatus | "DRAFT", string> = {
    DRAFT: "Brouillon",
    PENDING: "En attente de modération",
    PUBLISHED: "Publié",
    REJECTED: "Refusé",
    ARCHIVED: "Archivé",
};

/** Demande vue par son auteur (`ClaimResponse`, backend). */
export interface Claim {
    id: number;
    placeId: number;
    placeName: string;
    placeCity: string | null;
    status: ClaimStatus;
    relationship: string | null;
    evidence: string | null;
    decisionReason: string | null;
    createdAt: string;
    submittedAt: string | null;
    decidedAt: string | null;
    updatedAt: string;
    evidencePurged: boolean;
}

/** Demande vue par un modérateur (`AdminClaimResponse`, backend). */
export interface AdminClaim extends Claim {
    requesterId: number | null;
    requesterEmail: string | null;
    moderatorId: number | null;
}

export interface ClaimReview {
    claim: AdminClaim;
    place: Place;
    placeStatus: PlaceStatus | "DRAFT";
    events: { actorId: number | null; fromStatus: ClaimStatus | null; toStatus: ClaimStatus; reason: string | null; occurredAt: string }[];
    concurrentClaims: AdminClaim[];
    managers: PlaceManager[];
}

/** Gestionnaire d'un lieu (`ManagerResponse`, backend). */
export interface PlaceManager {
    userId: number;
    email: string;
    claimId: number | null;
    grantedAt: string;
}

/** Lieu géré (`ManagedPlaceResponse`, backend). */
export interface ManagedPlace {
    id: number;
    name: string;
    placeType: string;
    status: PlaceStatus | "DRAFT";
    address: string | null;
    postalCode: string | null;
    city: string | null;
    latitude: number | null;
    longitude: number | null;
    description: string | null;
    website: string | null;
    phone: string | null;
    email: string | null;
    origin: "CREATED" | "IMPORTED";
    editable: boolean;
    reportedSimilarTo: number | null;
    /** Lieu publié : ses informations se modifient par une proposition validée par un modérateur. */
    changeProposable: boolean;
}

/** Statut d'une proposition de modification d'un lieu publié (`ChangeStatus`, backend). */
export type PlaceChangeStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";

export const PLACE_CHANGE_STATUS_LABELS: Record<PlaceChangeStatus, string> = {
    PENDING: "En attente de modération",
    APPROVED: "Validée",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
};

/** Champs modifiables d'un lieu publié, par proposition (nom et catégorie exclus). */
export const PLACE_CHANGE_FIELDS = [
    { key: "address", label: "Adresse" },
    { key: "postalCode", label: "Code postal" },
    { key: "city", label: "Commune" },
    { key: "description", label: "Description" },
    { key: "website", label: "Site web" },
    { key: "phone", label: "Téléphone" },
    { key: "email", label: "E-mail" },
] as const;

export type PlaceChangeField = (typeof PLACE_CHANGE_FIELDS)[number]["key"];

/** Proposition de modification vue par son auteur (`PlaceChangeResponse`, backend). */
export interface PlaceChange {
    id: number;
    placeId: number;
    placeName: string;
    status: PlaceChangeStatus;
    address: string | null;
    postalCode: string | null;
    city: string | null;
    latitude: number | null;
    longitude: number | null;
    description: string | null;
    website: string | null;
    phone: string | null;
    email: string | null;
    decisionReason: string | null;
    createdAt: string;
    decidedAt: string | null;
}

/** Proposition vue par un modérateur (`AdminPlaceChangeResponse`, backend). */
export interface AdminPlaceChange {
    change: PlaceChange;
    requesterId: number | null;
    requesterEmail: string | null;
    moderatorId: number | null;
}

/** Dossier d'une proposition : fiche actuelle et gestionnaires (`PlaceChangeReviewResponse`, backend). */
export interface PlaceChangeReview {
    proposal: AdminPlaceChange;
    current: ManagedPlace;
    managers: PlaceManager[];
}

/** Lieu existant proche d'un lieu à créer (`SimilarPlaceResponse`, backend). */
export interface SimilarPlace {
    place: Place;
    status: PlaceStatus;
    distanceMeters: number | null;
}

export interface SimilarResult {
    latitude: number | null;
    longitude: number | null;
    candidates: SimilarPlace[];
}
