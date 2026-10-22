import type { AccountLimits, Plan } from '@/api';

/**
 * Limites des offres (décision du 22/10/2026), sans dépendance à React Native pour rester testables. Le backend
 * reste seul juge : au-delà, l'ajout est refusé (403) avec un message affiché tel quel.
 */

/** Ce que permet chaque offre, pour l'écran « Mon abonnement ». */
export const PLAN_LIMITS: Record<Plan, readonly string[]> = {
  FREE: ['5 favoris', '1 alerte'],
  PREMIUM: ['Favoris illimités', '10 alertes', '2 contributions par mois'],
  PRO: ['Favoris illimités', '10 alertes', 'Contributions illimitées', 'Espace professionnel'],
};

/** La limite est atteinte (`max` à `null` : illimité). */
export function isAtLimit(count: number, max: number | null | undefined): boolean {
  return max !== null && max !== undefined && count >= max;
}

/** « 3 / 5 favoris » ; `null` quand c'est illimité ou inconnu. */
export function formatUsage(
  count: number,
  max: number | null | undefined,
  noun: { one: string; many: string },
): string | null {
  if (max === null || max === undefined) return null;
  return `${count} / ${max} ${max > 1 ? noun.many : noun.one}`;
}

export const FAVORITES_NOUN = { one: 'favori', many: 'favoris' };
export const ALERTS_NOUN = { one: 'alerte', many: 'alertes' };

/**
 * Quota mensuel de contributions d'un compte Premium : texte « Contributions ce mois-ci : x / 2 » et limite
 * atteinte. `null` sans quota (Pro, administrateur) ou sans droit de contribuer (Gratuit, 0).
 */
export function contributionQuota(
  limits: AccountLimits | null | undefined,
): { text: string; atLimit: boolean } | null {
  if (!limits || limits.monthlyContributions === null || limits.monthlyContributions <= 0) return null;
  const { contributionsThisMonth: count, monthlyContributions: max } = limits;
  return { text: `Contributions ce mois-ci : ${count} / ${max}`, atLimit: count >= max };
}
