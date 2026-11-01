/**
 * Mots-clés d'une alerte (Premium et Pro), mêmes règles que le serveur et le site web, sans dépendance à React
 * Native pour rester testables.
 */

export const MAX_ALERT_KEYWORDS = 10;
const MIN_KEYWORD_LENGTH = 2;
const MAX_KEYWORD_LENGTH = 60;

/** Forme de comparaison : minuscules, sans accents ni ponctuation. */
export function comparableKeyword(keyword: string): string {
  return keyword
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/œ/g, 'oe')
    .replace(/æ/g, 'ae')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

/**
 * Ajoute à `current` les mots-clés saisis dans `text` (séparés par des virgules), sans doublon : la nouvelle liste,
 * ou un message d'erreur.
 */
export function addKeywords(current: readonly string[], text: string): { keywords: string[] } | { error: string } {
  const next = [...current];
  for (const part of text.split(',')) {
    const keyword = part.trim().replace(/\s+/g, ' ');
    if (keyword === '') continue;
    if (keyword.length < MIN_KEYWORD_LENGTH || keyword.length > MAX_KEYWORD_LENGTH) {
      return { error: `Un mot-clé fait de ${MIN_KEYWORD_LENGTH} à ${MAX_KEYWORD_LENGTH} caractères.` };
    }
    const comparable = comparableKeyword(keyword);
    if (comparable === '') return { error: 'Un mot-clé doit contenir au moins une lettre ou un chiffre.' };
    if (!next.some((existing) => comparableKeyword(existing) === comparable)) next.push(keyword);
  }
  if (next.length > MAX_ALERT_KEYWORDS) return { error: `Une alerte a ${MAX_ALERT_KEYWORDS} mots-clés au plus.` };
  return { keywords: next };
}

/** « « jazz », « marché de Noël » » : mots-clés affichés sous une alerte. */
export function formatKeywords(keywords: readonly string[] | undefined): string {
  return (keywords ?? []).map((keyword) => `« ${keyword} »`).join(', ');
}
