/**
 * Règles de l'accueil de première connexion, sans dépendance à React Native
 * pour rester testables.
 */

/** L'accueil s'affiche tant que l'utilisateur n'a jamais enregistré ses préférences. */
export function needsOnboarding(preferences: { configured?: boolean }): boolean {
  return preferences.configured === false;
}

/** Étape 1 (obligatoire) : message d'erreur à afficher, ou `null` si la saisie est complète. */
export function preferencesError(city: string, categoryCodes: readonly string[]): string | null {
  if (city.trim() === '') return 'Indiquez votre ville, ou utilisez votre position.';
  if (categoryCodes.length === 0) return 'Choisissez au moins une catégorie.';
  return null;
}

/** Étape 2 (facultative) : message d'erreur à afficher, ou `null` si l'alerte peut être créée. */
export function alertError(
  categoryCodes: readonly string[],
  includeFoodTrucks: boolean,
  notifyPush: boolean,
  notifyEmail: boolean,
): string | null {
  if (categoryCodes.length === 0 && !includeFoodTrucks) {
    return 'Choisissez au moins une catégorie, ou les food trucks.';
  }
  if (!notifyPush && !notifyEmail) return 'Choisissez au moins un moyen d’être prévenu.';
  return null;
}
