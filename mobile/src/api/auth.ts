import { request } from './http';
import type { CallOptions } from './activities';
import type { LoginRequest, LoginResponse, RegisterRequest, UserProfile } from './types';

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
