/**
 * L'application exige un compte : sans session, seuls l'écran de connexion / inscription (onglet Compte) et
 * « Mot de passe oublié » restent accessibles. Les textes légaux s'ouvrent dans le navigateur.
 */
export function isOpenWithoutAccount(segments: readonly string[]): boolean {
  const [first, second] = segments;
  if (first === 'mot-de-passe-oublie') return true;
  return first === '(tabs)' && second === 'compte';
}
