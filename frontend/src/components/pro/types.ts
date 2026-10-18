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
    managers: { userId: number; email: string; claimId: number | null; grantedAt: string }[];
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
