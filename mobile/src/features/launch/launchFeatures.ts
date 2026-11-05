/**
 * Fonctions ouvertes progressivement après le lancement (05/11/2026, PACA et Occitanie) : lieux, food trucks,
 * offres Premium et Pro. Le serveur décide (`GET /api/v1/features`) : en ouvrir une ne demande pas de nouvelle
 * version de l'application. Sans dépendance à React Native, pour rester testable.
 */
export interface LaunchFeatures {
  places: boolean;
  foodTrucks: boolean;
  premium: boolean;
  pro: boolean;
}

/** Tout ouvert : valeur hors fournisseur (tests, écrans rendus seuls), comportement d'avant le lancement. */
export const ALL_OPEN: LaunchFeatures = { places: true, foodTrucks: true, premium: true, pro: true };

/** Tout fermé : tant que le serveur n'a pas répondu, ou s'il ne répond pas. */
export const ALL_CLOSED: LaunchFeatures = { places: false, foodTrucks: false, premium: false, pro: false };

/** Réponse du serveur ramenée à des booléens : un champ absent ou illisible compte comme fermé. */
export function parseLaunchFeatures(value: unknown): LaunchFeatures {
  const data = (value ?? {}) as Partial<Record<keyof LaunchFeatures, unknown>>;
  return {
    places: data.places === true,
    foodTrucks: data.foodTrucks === true,
    premium: data.premium === true,
    pro: data.pro === true,
  };
}

/** Une offre au moins (Premium ou Pro) est ouverte : abonnement, contributions et leurs invitations. */
export function hasOpenOffer(features: LaunchFeatures): boolean {
  return features.premium || features.pro;
}
