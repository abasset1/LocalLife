import { request } from './http';
import type { CallOptions } from './activities';

/** `UserResponse` (backend) : profil du compte connecté, sans secret. */
export interface CurrentAccount {
  id: number;
  username: string;
  email: string;
  /** Rôle tel que relu en base par le serveur, pas celui du jeton stocké sur le téléphone. */
  role: 'USER' | 'PRO' | 'ADMIN';
  createdAt: string | null;
}

/** `GET /api/v1/users/me` : 401 si la session a expiré ou si le compte est suspendu. */
export function getCurrentAccount(options: CallOptions = {}): Promise<CurrentAccount> {
  return request<CurrentAccount>('/api/v1/users/me', { authenticated: true, signal: options.signal });
}
