/**
 * Règles du nom d'utilisateur (LS-000), identiques à `UsernamePolicy` côté
 * backend : elles donnent un retour immédiat pendant la saisie. Le backend
 * reste seul juge à l'inscription.
 */
export const USERNAME_MIN_LENGTH = 3;
export const USERNAME_MAX_LENGTH = 30;
export const USERNAME_RULES =
  '3 à 30 caractères : lettres sans accent, chiffres, « . », « _ » ou « - », en commençant par une lettre ou un chiffre.';

const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

/** Problème de format détectable sans appeler le serveur, ou `null`. */
export function usernameFormatError(username: string): string | null {
  const value = username.trim();
  if (value.length < USERNAME_MIN_LENGTH || value.length > USERNAME_MAX_LENGTH) {
    return `Le nom d'utilisateur doit contenir entre ${USERNAME_MIN_LENGTH} et ${USERNAME_MAX_LENGTH} caractères.`;
  }
  if (!USERNAME_PATTERN.test(value)) {
    return 'Lettres sans accent, chiffres, « . », « _ » ou « - » uniquement, en commençant par une lettre ou un chiffre.';
  }
  return null;
}
