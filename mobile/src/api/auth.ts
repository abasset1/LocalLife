import { request } from './http';
import type { CallOptions } from './activities';
import type { LoginRequest, LoginResponse, RegisterRequest, UserProfile, UsernameAvailability } from './types';

/**
 * Endpoints d'authentification (publics : jamais de JWT envoyé).
 *
 * Ce module ne fait que les appels HTTP. Le stockage sécurisé du JWT, la
 * restauration de session et la déconnexion relèvent de LL-MOB-0004.
 *
 * Erreurs à connaître côté écran :
 * - `login` : 401 = identifiants refusés, 403 = e-mail non vérifié ;
 * - `register` : 400 = données invalides (message du backend).
 */

const BASE = '/api/v1/auth';

/** `POST /auth/register` — crée le compte (un e-mail de vérification est envoyé). */
export function register(payload: RegisterRequest, options: CallOptions = {}): Promise<UserProfile> {
  return request<UserProfile>(`${BASE}/register`, {
    method: 'POST',
    body: payload,
    signal: options.signal,
  });
}

/** `POST /auth/login` — retourne le JWT. */
export function login(payload: LoginRequest, options: CallOptions = {}): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/login`, {
    method: 'POST',
    body: payload,
    signal: options.signal,
  });
}

/** `GET /auth/username-availability` — le nom d'utilisateur est-il valide et libre (LS-000) ? */
export function checkUsernameAvailability(username: string, options: CallOptions = {}): Promise<UsernameAvailability> {
  return request<UsernameAvailability>(`${BASE}/username-availability`, {
    query: { username },
    signal: options.signal,
  });
}

/** Réponse générique des endpoints qui n'ont qu'un message à afficher. */
export interface MessageResponse {
  message: string;
}

/**
 * `POST /auth/forgot-password` — envoie un lien de réinitialisation. Réponse identique que le compte
 * existe ou non (pas d'énumération des comptes) ; 400 seulement si l'e-mail est malformé.
 */
export function forgotPassword(email: string, options: CallOptions = {}): Promise<MessageResponse> {
  return request<MessageResponse>(`${BASE}/forgot-password`, {
    method: 'POST',
    body: { email },
    signal: options.signal,
  });
}

/** `POST /auth/resend-verification` — renvoie le lien de confirmation (au plus un par minute). */
export function resendVerification(email: string, options: CallOptions = {}): Promise<MessageResponse> {
  return request<MessageResponse>(`${BASE}/resend-verification`, {
    method: 'POST',
    body: { email },
    signal: options.signal,
  });
}

/**
 * `POST /auth/google` — connexion (ou création de compte) avec l'ID token Google. Le backend vérifie
 * le token (signature, audience = son `google.client-id`, e-mail vérifié) ; 401 s'il est refusé.
 */
export function loginWithGoogle(idToken: string, options: CallOptions = {}): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/google`, {
    method: 'POST',
    body: { idToken },
    signal: options.signal,
  });
}
