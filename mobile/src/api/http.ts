import { apiUrl } from '@/config/env';

import {
  ApiError,
  ApiErrorBody,
  HttpError,
  NetworkError,
  RequestAbortedError,
  ResponseFormatError,
  TimeoutError,
  defaultHttpMessage,
} from './errors';

/**
 * Cœur du client API (LL-MOB-0003) : seul endroit du mobile qui construit
 * des URLs et appelle `fetch`. Les écrans passent par les modules
 * spécialisés (`activities.ts`, `auth.ts`…), jamais par ce fichier
 * directement.
 */

const DEFAULT_TIMEOUT_MS = 15_000;

/**
 * Hooks fournis par la couche d'authentification (LL-MOB-0004), qui gère
 * le stockage sécurisé du JWT. Le client API ne stocke rien lui-même.
 */
export interface ApiClientConfig {
  /** Retourne le JWT courant, ou `null` si l'utilisateur n'est pas connecté. */
  getToken: () => string | null | Promise<string | null>;
  /** Appelé quand une requête authentifiée reçoit un 401 (JWT invalide/expiré). */
  onUnauthorized?: () => void;
}

let clientConfig: ApiClientConfig = { getToken: () => null };

export function configureApiClient(config: ApiClientConfig): void {
  clientConfig = config;
}

export type QueryValue = string | number | boolean | readonly string[] | null | undefined;
export type QueryParams = Record<string, QueryValue>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: QueryParams;
  body?: unknown;
  /**
   * `true` pour les endpoints protégés : le JWT est alors joint (`Bearer`).
   * `false` (défaut) pour les endpoints publics : le JWT n'est JAMAIS envoyé.
   *
   * ⚠️ Le `JwtFilter` du backend renvoie 401 dès qu'un en-tête
   * `Authorization` invalide/expiré est présent, même sur une route
   * publique : joindre le token partout casserait la lecture publique
   * (même règle que `frontend/src/api/apiClient.ts`).
   *
   * `'optional'` pour les endpoints publics dont la réponse dépend de
   * l'appelant (ex. fiche détaillée ou résumé) : le JWT est joint s'il y en
   * a un, et si le backend le refuse (401) la requête est rejouée sans lui.
   */
  authenticated?: boolean | 'optional';
  /** Annulation par l'appelant (ex. déplacement de carte avant la fin du chargement). */
  signal?: AbortSignal;
  timeoutMs?: number;
}

function buildQueryString(query: QueryParams | undefined): string {
  if (!query) return '';
  const parts: string[] = [];
  for (const [key, raw] of Object.entries(query)) {
    if (raw === undefined || raw === null) continue;
    const value = typeof raw === 'object' ? raw.join(',') : String(raw);
    if (value === '') continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
  }
  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

export function buildUrl(path: string, query?: QueryParams): string {
  const base = apiUrl.replace(/\/+$/, '');
  return `${base}${path}${buildQueryString(query)}`;
}

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text === '') return null;
  try {
    return JSON.parse(text);
  } catch {
    throw new ResponseFormatError();
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    const text = await response.text();
    if (text === '') return null;
    const parsed: unknown = JSON.parse(text);
    return parsed !== null && typeof parsed === 'object' ? (parsed as ApiErrorBody) : null;
  } catch {
    return null;
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { authenticated = false } = options;
  const token = authenticated ? await clientConfig.getToken() : null;
  try {
    return await send<T>(path, options, token);
  } catch (e) {
    // Token refusé sur un endpoint public : la lecture anonyme reste possible.
    if (authenticated === 'optional' && token && e instanceof HttpError && e.isUnauthorized) {
      return send<T>(path, options, null);
    }
    throw e;
  }
}

async function send<T>(path: string, options: RequestOptions, token: string | null): Promise<T> {
  const {
    method = 'GET',
    query,
    body,
    authenticated = false,
    signal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
  } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  // Timeout + annulation appelant combinés dans un seul AbortController.
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const onCallerAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', onCallerAbort, { once: true });
  }

  // Le timeout et l'annulation couvrent aussi la lecture du corps : une
  // requête annulée ou expirée ne doit jamais aboutir, même si les en-têtes
  // sont déjà arrivés.
  try {
    const response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });

    if (!response.ok) {
      const errorBody = await readErrorBody(response);
      if (controller.signal.aborted) throw new RequestAbortedError();
      if (response.status === 401 && (authenticated === true || token)) {
        clientConfig.onUnauthorized?.();
      }
      throw new HttpError(
        response.status,
        errorBody?.message || defaultHttpMessage(response.status),
        errorBody,
      );
    }

    const data = await parseJson(response);
    if (controller.signal.aborted) throw new RequestAbortedError();
    return data as T;
  } catch (e) {
    if (timedOut) throw new TimeoutError();
    if (signal?.aborted) throw new RequestAbortedError();
    if (e instanceof ApiError) throw e;
    throw new NetworkError();
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', onCallerAbort);
  }
}
