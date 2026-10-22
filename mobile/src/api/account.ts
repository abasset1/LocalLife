import { request } from './http';
import type { CallOptions } from './activities';

/**
 * Limites de l'offre du compte (décision du 22/10/2026) ; `null` = illimité. Les éléments déjà enregistrés au-delà
 * restent ; seuls les ajouts sont refusés (403 avec un message à afficher tel quel).
 */
export interface AccountLimits {
  /** Favoris : 5 en Gratuit, illimités sinon. */
  maxLikes: number | null;
  /** Alertes : 1 en Gratuit, 10 sinon. */
  maxAlerts: number;
  /** Nouvelles contributions par mois civil (heure de Paris) : 0 en Gratuit, 2 en Premium, illimitées en Pro. */
  monthlyContributions: number | null;
  /** Contributions de ce mois qui comptent (ni refusées ni modifications). */
  contributionsThisMonth: number;
}

/** `UserResponse` (backend) : profil du compte connecté, sans secret. */
export interface CurrentAccount {
  id: number;
  username: string;
  email: string;
  /** Rôle tel que relu en base par le serveur, pas celui du jeton stocké sur le téléphone. */
  role: 'USER' | 'PRO' | 'ADMIN';
  createdAt: string | null;
  /** Nouvelle adresse en attente de confirmation, `null` sinon. */
  pendingEmail?: string | null;
  /** Faux pour un compte créé avec Google, sans mot de passe LocalLife. */
  hasPassword?: boolean;
  /** Droit de proposer ou modifier une activité (offre Premium ou Pro). */
  canContribute?: boolean;
  /**
   * Accès à l'espace professionnel (offre Pro en cours ou rôle attribué) : seul indicateur pour afficher l'espace
   * pro et les liens « Demander la gestion de ce lieu ». Absent d'un ancien serveur : traité comme faux.
   */
  hasProAccess?: boolean;
  /** Limites de l'offre ; absentes d'un ancien serveur. */
  limits?: AccountLimits;
}

/** `GET /api/v1/users/me` : 401 si la session a expiré ou si le compte est suspendu. */
export function getCurrentAccount(options: CallOptions = {}): Promise<CurrentAccount> {
  return request<CurrentAccount>('/api/v1/users/me', { authenticated: true, signal: options.signal });
}

/** `POST /api/v1/users/me/password` : nouveau jeton de la session ; les autres sessions sont fermées. */
export function changePassword(
  currentPassword: string | null,
  newPassword: string,
  options: CallOptions = {},
): Promise<{ token: string }> {
  return request<{ token: string }>('/api/v1/users/me/password', {
    method: 'POST',
    authenticated: true,
    body: currentPassword === null ? { newPassword } : { currentPassword, newPassword },
    signal: options.signal,
  });
}

/** `POST /api/v1/users/me/deletion` : définitif ; mot de passe, ou `SUPPRIMER` pour un compte sans mot de passe. */
export function deleteAccount(secret: { password: string } | { confirmation: string }, options: CallOptions = {}) {
  return request<null>('/api/v1/users/me/deletion', {
    method: 'POST',
    authenticated: true,
    body: secret,
    signal: options.signal,
  });
}
