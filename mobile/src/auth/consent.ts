import { HttpError } from '@/api/errors';
import type { RegisterRequest } from '@/api/types';

/**
 * Consentement à l'inscription (contrat RGPD du 22/10/2026) : âge minimal et acceptation des conditions
 * d'utilisation et de la politique de confidentialité. Le serveur refuse toute création de compte sans lui
 * (400 à l'inscription, 428 à la connexion Google) ; l'écran ne fait que recueillir la case et afficher ses messages.
 */

export const CONSENT_LABEL =
  "J'ai au moins 15 ans et j'accepte les conditions d'utilisation et la politique de confidentialité";

/** Affiché avant tout appel si la case n'est pas cochée (le bouton est normalement désactivé). */
export const CONSENT_REQUIRED_MESSAGE =
  "Cochez la case : vous devez avoir au moins 15 ans et accepter les conditions d'utilisation et la politique de confidentialité.";

export interface RegistrationForm {
  username: string;
  email: string;
  password: string;
  acceptTerms: boolean;
}

/** Corps de `POST /auth/register`, ou `null` tant que la case n'est pas cochée. */
export function buildRegisterRequest(form: RegistrationForm): RegisterRequest | null {
  if (form.acceptTerms !== true) return null;
  return { username: form.username.trim(), email: form.email.trim(), password: form.password, acceptTerms: true };
}

/** `POST /auth/google` a répondu 428 : le compte serait créé, il faut d'abord la case de consentement. */
export function isTermsConsentRequired(error: unknown): boolean {
  return error instanceof HttpError && error.status === 428;
}

/**
 * Relance la connexion Google avec `acceptTerms: true` après un 428. L'ID token Google déjà obtenu est réutilisé ;
 * s'il est refusé (401 : expiré entre-temps), le sélecteur Google est rouvert pour en obtenir un nouveau.
 * Renvoie `false` si l'utilisateur annule ce second passage. Les autres erreurs sont propagées.
 */
export async function retryGoogleWithConsent(
  idToken: string,
  login: (idToken: string, acceptTerms: true) => Promise<void>,
  requestNewIdToken: () => Promise<string | null>,
): Promise<boolean> {
  try {
    await login(idToken, true);
    return true;
  } catch (e) {
    if (!(e instanceof HttpError && e.isUnauthorized)) throw e;
  }
  const fresh = await requestNewIdToken();
  if (!fresh) return false;
  await login(fresh, true);
  return true;
}
