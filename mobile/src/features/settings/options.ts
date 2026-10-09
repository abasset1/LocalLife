/** Rayons proposés (km) — bornés à 50 comme la recherche `/nearby` du backend. */
export const RADIUS_OPTIONS_KM = [5, 10, 20, 30, 50] as const;

/** Rayon par défaut d'une nouvelle alerte quand aucun paramètre de recherche n'en fixe un. */
export const DEFAULT_ALERT_RADIUS_KM = 10;

/** Ajoute ou retire `code` de la sélection, en conservant l'ordre. */
export function toggleCode(selected: readonly string[], code: string): string[] {
  return selected.includes(code) ? selected.filter((value) => value !== code) : [...selected, code];
}

/** Libellés des catégories, dans l'ordre de la sélection ; code brut si inconnu. */
export function categoryLabels(codes: readonly string[], categories: readonly { code: string; label: string }[]): string {
  return codes.map((code) => categories.find((category) => category.code === code)?.label ?? code).join(', ');
}
