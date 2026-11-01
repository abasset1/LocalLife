import type { FollowTargetType, FollowedItem } from '@/api';

/**
 * Règles du suivi (cloche « Suivre »), sans dépendance à React Native pour rester testables.
 */

/** Clé unique d'une cible suivie : une activité et un lieu peuvent avoir le même identifiant. */
export function followKey(type: FollowTargetType, id: number): string {
  return `${type}-${id}`;
}

/** Suivis avec ou sans la cible donnée, sans doublon ; la cible ajoutée passe en tête (la plus récente). */
export function withFollow(
  items: readonly FollowedItem[],
  target: Pick<FollowedItem, 'type' | 'id' | 'title' | 'city'>,
  following: boolean,
  now: Date = new Date(),
): FollowedItem[] {
  const others = items.filter((item) => followKey(item.type, item.id) !== followKey(target.type, target.id));
  if (!following) return others;
  return [{ ...target, status: 'PUBLISHED', followedAt: now.toISOString() }, ...others];
}

/** État affiché à côté d'un suivi : « annulée », « retiré de la carte »… ; `null` s'il est toujours visible. */
export function followStatusLabel(item: FollowedItem): string | null {
  if (item.type === 'place') return item.status && item.status !== 'PUBLISHED' ? 'retiré de la carte' : null;
  switch (item.status) {
    case 'CANCELLED':
      return 'annulée';
    case 'ARCHIVED':
      return 'terminée';
    case 'DELETED':
      return 'retirée';
    default:
      return null;
  }
}

/** Message affiché quand le suivi est refusé à un compte gratuit (403), si le serveur n'en donne pas. */
export const FOLLOW_PLAN_MESSAGE = 'Suivre une activité ou un lieu est réservé aux offres Premium et Pro.';
