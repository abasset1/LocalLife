import type { FollowTargetType, FollowedItem } from '@/api';
import type { PushRegistration } from '@/notifications';

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
  if (item.type !== 'activity') return item.status && item.status !== 'PUBLISHED' ? 'retiré de la carte' : null;
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

/** Icône et nature d'un suivi, pour la liste « Mes suivis ». */
export const FOLLOW_TYPE_LABELS: Record<FollowTargetType, { icon: string; label: string }> = {
  activity: { icon: '📅', label: 'Activité' },
  place: { icon: '🏛', label: 'Lieu' },
  foodtruck: { icon: '🚚', label: 'Food truck' },
};

/** Message affiché quand le suivi est refusé à un compte gratuit (403), si le serveur n'en donne pas. */
export const FOLLOW_PLAN_MESSAGE = 'Le suivi est réservé aux offres Premium et Pro.';

/** Message affiché quand les notifications sont refusées au moment de suivre : les mises à jour partent par e-mail. */
export const FOLLOW_PUSH_DENIED_MESSAGE =
  'Vous serez prévenu de ses mises à jour par e-mail. Pour les recevoir sur ce téléphone, autorisez les ' +
  'notifications de Neiri dans les réglages.';

/**
 * Après un nouveau suivi, la permission de notifier est demandée (le backend envoie les mises à jour en push dès
 * qu'un appareil est enregistré, sinon par e-mail). Le suivi est enregistré quoi qu'il arrive ; seul un refus est
 * signalé, et une seule fois (`alreadyNoticed`) pour ne pas le répéter à chaque cloche. Une indisponibilité (web,
 * émulateur, version sans notifications…) reste silencieuse : l'utilisateur n'y peut rien, l'e-mail prend le relais.
 */
export function followPushNotice(result: PushRegistration, alreadyNoticed: boolean): string | null {
  return result.status === 'denied' && !alreadyNoticed ? FOLLOW_PUSH_DENIED_MESSAGE : null;
}
