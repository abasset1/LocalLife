/**
 * Types des réponses/requêtes de l'API LocalLife (LL-MOB-0003).
 *
 * Miroirs des DTO du backend Spring Boot (`backend/.../api/*.java`). Les
 * `LocalDateTime` Java sont sérialisés en chaînes ISO-8601 sans fuseau
 * (`2026-09-23T10:00:00`), les `Instant` avec fuseau (`...Z`).
 */

/**
 * Précision de la position (ADR-0011, phase A) : `APPROXIMATE` = une zone
 * (« Divers lieux », « Centre ville »), pas le lieu. Facultatif : absent d'un
 * backend antérieur, traité comme `UNKNOWN`.
 */
export type CoordinatePrecision = 'EXACT' | 'APPROXIMATE' | 'UNKNOWN';

/** `ActivitySummaryResponse` — liste (`GET /activities`) et fiche résumée. */
export interface ActivitySummary {
  id: number;
  title: string;
  category: string;
  date: string | null;
  city: string | null;
  address: string | null;
  latitude: number;
  longitude: number;
  mainImageUrl: string | null;
  /** Contributeur (`null` pour une activité importée). */
  createdBy?: number | null;
  coordinatePrecision?: CoordinatePrecision;
}

/** `ActivityResponse` — résultats de `nearby` et `within-bounds`. */
export interface Activity {
  id: number;
  title: string;
  description: string | null;
  category: string;
  latitude: number;
  longitude: number;
  startDate: string | null;
  endDate: string | null;
  status: string;
  sourceName: string | null;
  url: string | null;
  address: string | null;
  city: string | null;
  postalCode: string | null;
  createdBy: number | null;
  createdByUsername: string | null;
  likedByCurrentUser: boolean;
  mainImageUrl: string | null;
  /** Premier passage au statut publié ; `null` si publiée avant le suivi de cette date. */
  publishedAt: string | null;
  coordinatePrecision?: CoordinatePrecision;
}

export interface ScheduleSummary {
  id: number;
  locationId: number | null;
  startTime: string | null;
  endTime: string | null;
  validFrom: string | null;
  validUntil: string | null;
  timezone: string | null;
  recurrenceRule: string | null;
}

export interface OccurrenceSummary {
  id: number;
  scheduleId: number | null;
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
  region: string | null;
  latitude: number | null;
  longitude: number | null;
  timezone: string | null;
  coordinatePrecision?: CoordinatePrecision;
}

export interface MediaSummary {
  id: number;
  url: string;
  type: string | null;
  width: number | null;
  height: number | null;
  credit: string | null;
  altText: string | null;
}

export interface LinkSummary {
  /** `WEBSITE`, `PHONE`, `EMAIL`… */
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

/** `ActivityDetailResponse` — fiche détaillée. */
export interface ActivityDetail {
  id: number;
  title: string;
  description: string | null;
  longDescription: string | null;
  category: string;
  latitude: number;
  longitude: number;
  startDate: string | null;
  endDate: string | null;
  status: string;
  url: string | null;
  address: string | null;
  city: string | null;
  postalCode: string | null;
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
  categoryCodes: string[];
  /** Premier passage au statut publié ; `null` si publiée avant le suivi de cette date. */
  publishedAt: string | null;
  /** Contributeur (`null` pour une activité importée). */
  createdBy?: number | null;
  coordinatePrecision?: CoordinatePrecision;
}

/** `PlaceResponse` — lieu permanent publié (ADR-0011, phase B), couche « Lieux » de la carte. */
export interface Place {
  id: number;
  name: string;
  /** Type DATAtourisme (`Museum`, `Theater`…). */
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

/**
 * `GET /activities/{id}` renvoie la fiche détaillée ou un résumé selon les
 * droits de l'appelant (`PremiumAccessPolicy`). Utiliser `isActivityDetail`
 * pour distinguer les deux.
 */
export type ActivityDetailOrSummary = ActivityDetail | ActivitySummary;

export function isActivityDetail(activity: ActivityDetailOrSummary): activity is ActivityDetail {
  return 'schedules' in activity;
}

/** `Category`. */
/** `CategoryResponse` (`GET /categories`) — le `code` est l'identifiant métier stable. */
export interface Category {
  code: string;
  label: string;
  description: string | null;
  active: boolean;
}

/** Commune proposée par `GET /geocoding/city-suggestions` (nom, codes postaux, département, centre). */
export interface CitySuggestion {
  name: string;
  postalCodes: string[];
  department: string | null;
  latitude: number;
  longitude: number;
}

/** `UserPreferencesResponse` (`GET`/`PUT /users/me/preferences`) — paramètres de recherche par défaut. */
export interface UserPreferences {
  defaultCity: string | null;
  defaultLatitude: number | null;
  defaultLongitude: number | null;
  defaultRadiusKm: number | null;
  defaultCategoryCodes: string[];
  /** `false` tant que l'utilisateur n'a jamais enregistré ses préférences (accueil de première connexion). */
  configured?: boolean;
}

/**
 * `UpdatePreferencesRequest`. Ville : par position (`defaultLatitude`/
 * `defaultLongitude`, prioritaire) ou par nom ; le backend vérifie qu'elle
 * existe et enregistre son nom officiel.
 */
export interface UpdatePreferencesRequest {
  defaultCity: string | null;
  defaultLatitude: number | null;
  defaultLongitude: number | null;
  defaultRadiusKm: number | null;
  defaultCategoryCodes: string[];
}

/** `CityResponse` (`GET /users/me/preferences/city`) — ville existante, nom officiel. */
export interface CityLookup {
  city: string;
  latitude: number;
  longitude: number;
}

/** `AlertResponse` (`/users/me/alerts`). */
export interface ActivityAlert {
  id: number;
  categoryCodes: string[];
  /** Nouveaux food trucks inclus (l'alerte peut n'avoir qu'eux). */
  includeFoodTrucks: boolean;
  city: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
  notifyEmail: boolean;
  notifyPush: boolean;
}

/** `CreateAlertRequest` — même règle de ville que `UpdatePreferencesRequest`. */
export interface CreateAlertRequest {
  categoryCodes: string[];
  includeFoodTrucks: boolean;
  city: string | null;
  latitude: number | null;
  longitude: number | null;
  radiusKm: number;
  notifyEmail: boolean;
  notifyPush: boolean;
}

/** `FoodTruck` (`GET /foodtrucks`, uniquement ceux au statut `PUBLISHED`). */
/** Type d'emplacement d'une présence (`SpotKind` backend, ADR-0013). */
export type SpotKind = 'MARKET' | 'PRIVATE' | 'EVENT' | 'OTHER';

/**
 * Food truck présent aujourd'hui à un emplacement (`FoodTruckMapResponse` backend, ADR-0013) : un élément
 * par présence ; `id` est celui du food truck, `latitude`/`longitude` celles de l'emplacement.
 */
export interface FoodTruck {
  id: number;
  name: string;
  description: string | null;
  latitude: number;
  longitude: number;
  category: string;
  contact: string | null;
  status: string;
  presenceId: number;
  spotName: string | null;
  spotKind: SpotKind;
  daysOfWeek: string | null;
  startTime: string | null;
  endTime: string | null;
  validFrom: string | null;
  validUntil: string | null;
  /** Lieu LocalLife où se trouve l'emplacement, ou `null`. */
  placeId: number | null;
}

/** Présence du planning d'un food truck (`PresenceResponse` backend). */
export type FoodTruckPresence = Pick<
  FoodTruck,
  'latitude' | 'longitude' | 'spotName' | 'spotKind' | 'daysOfWeek' | 'startTime' | 'endTime' | 'validFrom'
  | 'validUntil' | 'placeId'
> & { id: number };

/** Fiche d'un food truck (`GET /foodtrucks/{id}`) : profil et planning, distincts. */
export interface FoodTruckDetail {
  id: number;
  name: string;
  description: string | null;
  category: string;
  contact: string | null;
  presences: FoodTruckPresence[];
}

/** Rôle utilisateur (`Role` backend). */
export type UserRole = string;

/** `UserResponse` — retourné par l'inscription. */
export interface UserProfile {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  createdAt: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

/** `GET /auth/username-availability` (LS-000) : `message` explique un refus. */
export interface UsernameAvailability {
  available: boolean;
  message: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** `LoginResponse`. */
export interface LoginResponse {
  token: string;
}

/**
 * `CreateActivityRequest` (contribution). Les dates sont optionnelles
 * (`yyyy-MM-dd` ou `yyyy-MM-ddTHH:mm`). L'adresse est un texte libre :
 * le géocodage est fait côté backend.
 */
export interface CreateActivityRequest {
  title: string;
  description?: string;
  /** Codes des catégories du référentiel (`CULTURE`…), au moins un. */
  categoryCodes: string[];
  address: string;
  /** Ignorées quand `recurrence` est renseignée (ses bornes `from`/`until` les remplacent). */
  startDate?: string;
  endDate?: string;
  url?: string;
  phone?: string;
  email?: string;
  /** Passage hebdomadaire (food truck, marché…). */
  recurrence?: RecurrenceRequest;
}

/** Récurrence hebdomadaire d'une contribution (`RecurrenceRequest` côté backend). */
export interface RecurrenceRequest {
  /** Codes RFC 5545 : `MO`, `TU`, `WE`, `TH`, `FR`, `SA`, `SU`. */
  daysOfWeek: string[];
  /** `HH:mm`. */
  startTime: string;
  endTime?: string | null;
  /** `yyyy-MM-dd`, aujourd'hui par défaut. */
  from?: string | null;
  until?: string | null;
}

/**
 * Contribution vue par son auteur (`ContributionResponse`) : « Mes contributions » et
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
  recurrence: RecurrenceRequest | null;
}

/**
 * Corps de la réponse `201` de `POST /activities` : l'entité `Activity`
 * du backend (statut initial `PENDING`, coordonnées géocodées).
 */
export interface CreatedActivity {
  id: number;
  title: string;
  description: string | null;
  category: string;
  latitude: number;
  longitude: number;
  startDate: string | null;
  endDate: string | null;
  status: string;
  url: string | null;
  address: string | null;
  city: string | null;
  postalCode: string | null;
}

/** Paramètres de `GET /activities/nearby` (contrat `GEO_SEARCH_CONTRACT.md`). */
export interface NearbySearchParams {
  latitude: number;
  longitude: number;
  /** Rayon en kilomètres, strictement positif, 50 max. */
  radiusKm: number;
  /** Une ou plusieurs catégories. */
  category?: string | readonly string[];
  /** `yyyy-MM-dd`. */
  date?: string;
  /** `yyyy-MM-dd`, avec `date` uniquement. */
  dateTo?: string;
}

/** Paramètres de `GET /activities/within-bounds` (contrat `BOUNDING_BOX_SEARCH_CONTRACT.md`). */
export interface WithinBoundsSearchParams {
  swLatitude: number;
  swLongitude: number;
  neLatitude: number;
  neLongitude: number;
  category?: string | readonly string[];
  date?: string;
  dateTo?: string;
}

/** Paramètres de `GET /activities` (liste). */
export interface ListActivitiesParams {
  city?: string;
  /** `yyyy-MM-dd`. */
  date?: string;
  /** Clés parmi `city` / `date`, ex. `['city', 'date']`. */
  sort?: readonly ('city' | 'date')[];
}
