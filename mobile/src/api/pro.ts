import { request } from './http';
import type { CallOptions } from './activities';
import type { Place, SpotKind } from './types';

/**
 * Espace professionnel (ADR-0012, ADR-0013), partagé avec le site web : demandes de gestion d'un lieu existant,
 * lieux créés, food trucks avec leurs emplacements et leur planning. JWT requis ; 403 sans offre Pro.
 */

export type ClaimStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'INFO_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN';

export type ProPlaceStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED';

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
  updatedAt: string;
}

export interface ManagedPlace {
  id: number;
  name: string;
  placeType: string;
  status: ProPlaceStatus;
  address: string | null;
  postalCode: string | null;
  city: string | null;
  description: string | null;
  website: string | null;
  phone: string | null;
  email: string | null;
  origin: 'CREATED' | 'IMPORTED';
  editable: boolean;
  /** Lieu publié, importé ou créé : on peut proposer une modification de sa fiche (`POST .../changes`). */
  changeProposable: boolean;
}

export type PlaceChangeStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

/** Proposition de modification d'un lieu géré, examinée par LocalLife (`PlaceChangeResponse`). */
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

/** Nom et catégorie ne se modifient pas ; `city` obligatoire, adresse géocodée sans position. */
export interface PlaceChangeInput {
  address: string;
  postalCode: string;
  city: string;
  latitude?: number | null;
  longitude?: number | null;
  description: string;
  website: string;
  phone: string;
  email: string;
}

export interface SimilarPlace {
  place: Place;
  status: ProPlaceStatus;
  distanceMeters: number | null;
}

export interface SimilarResult {
  latitude: number | null;
  longitude: number | null;
  candidates: SimilarPlace[];
}

export interface NewPlaceInput {
  name: string;
  placeType: string;
  address: string;
  postalCode: string;
  city: string;
  latitude: number | null;
  longitude: number | null;
  description: string;
  website: string;
  phone: string;
  email: string;
  confirmNotDuplicate: boolean;
  reportedSimilarTo: number | null;
}

export type TruckStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'SUSPENDED' | 'ARCHIVED' | 'REJECTED';
export type PresenceStatus = 'DRAFT' | 'PUBLISHED' | 'SUSPENDED' | 'ARCHIVED';
export type OwnerAction = 'publish' | 'suspend' | 'archive';

export interface OwnedFoodTruck {
  id: number;
  name: string;
  description: string | null;
  category: string;
  contact: string | null;
  status: TruckStatus;
  suspendedByLocalLife: boolean;
  /** Validé une fois par LocalLife : ensuite, « Publier » le publie directement (sinon il part en validation). */
  approved: boolean;
  /** Motif d'un refus (`REJECTED`), `null` sinon. */
  reviewReason: string | null;
  /** Masqué parce que l'accès professionnel a pris fin ; republié automatiquement s'il revient. */
  suspendedForAccess: boolean;
}

export interface ProfileInput {
  name: string;
  category: string;
  description: string;
  contact: string;
}

export interface ManagedPresence {
  id: number;
  status: PresenceStatus;
  spotName: string | null;
  spotKind: SpotKind;
  daysOfWeek: string | null;
  startTime: string | null;
  endTime: string | null;
  validFrom: string | null;
  validUntil: string | null;
  placeId: number | null;
}

/** Présence saisie : un lieu LocalLife publié (`placeId`) ou une adresse (`newSpot`) ; aucun des deux = inchangé. */
export interface PresenceInput {
  placeId?: number;
  newSpot?: {
    name: string;
    address: string;
    postalCode: string;
    city: string;
    latitude: number | null;
    longitude: number | null;
  };
  spotKind: SpotKind;
  days: string[];
  startTime: string | null;
  endTime: string | null;
  validFrom: string | null;
  validUntil: string | null;
}

const PRO = '/api/v1/pro';

const auth = { authenticated: true } as const;

// ---------------------------------------------------------------- lieux et demandes de gestion

export function listMyPlaces(options: CallOptions = {}): Promise<ManagedPlace[]> {
  return request<ManagedPlace[]>(`${PRO}/places`, { ...auth, signal: options.signal });
}

export function getMyPlace(id: number, options: CallOptions = {}): Promise<ManagedPlace> {
  return request<ManagedPlace>(`${PRO}/places/${id}`, { ...auth, signal: options.signal });
}

export function submitPlace(id: number): Promise<ManagedPlace> {
  return request<ManagedPlace>(`${PRO}/places/${id}/submit`, { method: 'POST', ...auth });
}

/** Lieux existants proches, ou de même nom dans la commune ; renvoie aussi la position déduite de l'adresse. */
export function findSimilarPlaces(
  query: { name?: string; address?: string; postalCode?: string; city?: string },
  options: CallOptions = {},
): Promise<SimilarResult> {
  return request<SimilarResult>(`${PRO}/places/similar`, { query, ...auth, signal: options.signal });
}

/** 409 avec `candidates` tant que les lieux proches ne sont pas écartés (`confirmNotDuplicate`). */
export function createPlace(input: NewPlaceInput): Promise<ManagedPlace> {
  return request<ManagedPlace>(`${PRO}/places`, { method: 'POST', body: input, ...auth });
}

/** Remplace la proposition en attente du même compte sur ce lieu. */
export function proposePlaceChange(placeId: number, input: PlaceChangeInput): Promise<PlaceChange> {
  return request<PlaceChange>(`${PRO}/places/${placeId}/changes`, { method: 'POST', body: input, ...auth });
}

/** Propositions de modification du lieu, les plus récentes d'abord. */
export function listPlaceChanges(placeId: number, options: CallOptions = {}): Promise<PlaceChange[]> {
  return request<PlaceChange[]>(`${PRO}/places/${placeId}/changes`, { ...auth, signal: options.signal });
}

export function withdrawPlaceChange(changeId: number): Promise<PlaceChange> {
  return request<PlaceChange>(`${PRO}/place-changes/${changeId}/withdraw`, { method: 'POST', ...auth });
}

/** Ne plus gérer ce lieu (204). */
export function renouncePlace(placeId: number): Promise<null> {
  return request<null>(`${PRO}/places/${placeId}/renounce`, { method: 'POST', ...auth });
}

export function listMyClaims(options: CallOptions = {}): Promise<Claim[]> {
  return request<Claim[]>(`${PRO}/claims`, { ...auth, signal: options.signal });
}

export function createClaim(placeId: number, text: { relationship: string; evidence: string }): Promise<Claim> {
  return request<Claim>(`${PRO}/places/${placeId}/claims`, { method: 'POST', body: text, ...auth });
}

export function updateClaim(id: number, text: { relationship: string; evidence: string }): Promise<Claim> {
  return request<Claim>(`${PRO}/claims/${id}`, { method: 'PATCH', body: text, ...auth });
}

export function claimAction(id: number, action: 'submit' | 'withdraw'): Promise<Claim> {
  return request<Claim>(`${PRO}/claims/${id}/${action}`, { method: 'POST', ...auth });
}

// ---------------------------------------------------------------- food trucks

export function listMyFoodTrucks(options: CallOptions = {}): Promise<OwnedFoodTruck[]> {
  return request<OwnedFoodTruck[]>(`${PRO}/foodtrucks`, { ...auth, signal: options.signal });
}

export function getMyFoodTruck(id: number, options: CallOptions = {}): Promise<OwnedFoodTruck> {
  return request<OwnedFoodTruck>(`${PRO}/foodtrucks/${id}`, { ...auth, signal: options.signal });
}

export function createFoodTruck(input: ProfileInput): Promise<OwnedFoodTruck> {
  return request<OwnedFoodTruck>(`${PRO}/foodtrucks`, { method: 'POST', body: input, ...auth });
}

export function updateFoodTruck(id: number, input: ProfileInput): Promise<OwnedFoodTruck> {
  return request<OwnedFoodTruck>(`${PRO}/foodtrucks/${id}`, { method: 'PUT', body: input, ...auth });
}

export function foodTruckAction(id: number, action: OwnerAction): Promise<OwnedFoodTruck> {
  return request<OwnedFoodTruck>(`${PRO}/foodtrucks/${id}/${action}`, { method: 'POST', ...auth });
}

export function listPresences(foodTruckId: number, options: CallOptions = {}): Promise<ManagedPresence[]> {
  return request<ManagedPresence[]>(`${PRO}/foodtrucks/${foodTruckId}/presences`, { ...auth, signal: options.signal });
}

/** Créée en brouillon ; 409 si elle chevauche un autre emplacement du food truck. */
export function createPresence(foodTruckId: number, input: PresenceInput): Promise<ManagedPresence> {
  return request<ManagedPresence>(`${PRO}/foodtrucks/${foodTruckId}/presences`, {
    method: 'POST',
    body: input,
    ...auth,
  });
}

export function updatePresence(id: number, input: PresenceInput): Promise<ManagedPresence> {
  return request<ManagedPresence>(`${PRO}/presences/${id}`, { method: 'PUT', body: input, ...auth });
}

export function presenceAction(id: number, action: OwnerAction): Promise<ManagedPresence> {
  return request<ManagedPresence>(`${PRO}/presences/${id}/${action}`, { method: 'POST', ...auth });
}
