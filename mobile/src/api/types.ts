/**
 * Types des réponses/requêtes de l'API LocalLife (LL-MOB-0003).
 *
 * Miroirs des DTO du backend Spring Boot (`backend/.../api/*.java`). Les
 * `LocalDateTime` Java sont sérialisés en chaînes ISO-8601 sans fuseau
 * (`2026-09-23T10:00:00`), les `Instant` avec fuseau (`...Z`).
 */

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

/** `UserPreferencesResponse` (`GET`/`PUT /users/me/preferences`) — paramètres de recherche par défaut. */
export interface UserPreferences {
  defaultCity: string | null;
  defaultLatitude: number | null;
  defaultLongitude: number | null;
  defaultRadiusKm: number | null;
  defaultCategoryCodes: string[];
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
export interface FoodTruck {
  id: number;
  name: string;
  description: string | null;
  latitude: number;
  longitude: number;
  category: string;
  contact: string | null;
  status: string;
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
  category: string;
  address: string;
  startDate?: string;
  endDate?: string;
  url?: string;
  phone?: string;
  email?: string;
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
