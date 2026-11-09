/** Rayons proposés (km) — bornés à 50 comme la recherche `/nearby` du backend. */
export const RADIUS_OPTIONS_KM = [5, 10, 20, 30, 50] as const;

/** Rayon par défaut d'une nouvelle alerte quand aucun paramètre de recherche n'en fixe un. */
export const DEFAULT_ALERT_RADIUS_KM = 10;

/** Ajoute ou retire `code` de la sélection, en conservant l'ordre. */
export function toggleCode(selected: readonly string[], code: string): string[] {
  return selected.includes(code) ? selected.filter((value) => value !== code) : [...selected, code];
}

/**
 * Valeur de la puce « Food trucks » parmi les catégories d'une alerte : elle
 * n'est pas un code de catégorie, elle active `includeFoodTrucks`.
 */
export const FOOD_TRUCKS_CHIP = '__food_trucks__';

/** Critères d'une alerte, lisibles : ses catégories, puis « Food trucks » si elle les couvre. */
export function alertCriteria(
  alert: { categoryCodes: readonly string[]; includeFoodTrucks: boolean },
  categories: readonly { code: string; label: string }[],
): string {
  const labels = categoryLabels(alert.categoryCodes, categories);
  if (!alert.includeFoodTrucks) return labels || 'Toutes catégories';
  return labels ? `${labels}, Food trucks` : 'Food trucks';
}

/** Libellés des catégories, dans l'ordre de la sélection ; code brut si inconnu. */
export function categoryLabels(codes: readonly string[], categories: readonly { code: string; label: string }[]): string {
  return codes.map((code) => categories.find((category) => category.code === code)?.label ?? code).join(', ');
}

/** Puce « Dernière utilisée » du choix de carte : aucune carte imposée (`defaultMapLayer` effacé). */
export const LAST_USED_MAP_LAYER = 'last';

const MAP_LAYER_CHOICES = [
  { value: 'activities', label: 'Activités' },
  { value: 'places', label: '🏛 Lieux' },
  { value: 'foodTrucks', label: '🚚 Food trucks' },
  { value: 'night', label: '🍸 Night' },
] as const;

/**
 * Cartes proposées comme carte à l'ouverture : les activités et « Night », et les lieux ou food trucks s'ils sont ouverts
 * (lancement progressif). Une carte déjà enregistrée reste proposée même refermée depuis.
 */
export function mapLayerOptions(
  features: { places: boolean; foodTrucks: boolean },
  current: string | null = null,
  withLastUsed = false,
): { value: string; label: string }[] {
  const layers = MAP_LAYER_CHOICES.filter(
    (choice) =>
      choice.value === 'activities' ||
      choice.value === 'night' ||
      choice.value === current ||
      (choice.value === 'places' ? features.places : features.foodTrucks),
  ).map((choice) => ({ value: choice.value, label: choice.label }));
  return withLastUsed ? [{ value: LAST_USED_MAP_LAYER, label: 'Dernière utilisée' }, ...layers] : layers;
}
