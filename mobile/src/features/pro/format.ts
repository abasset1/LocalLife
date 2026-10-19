import type {
  ClaimStatus,
  OwnerAction,
  Plan,
  PresenceStatus,
  ProPlaceStatus,
  SubscriptionOverview,
  TruckStatus,
} from '@/api';

export const PLAN_LABELS: Record<Plan, string> = { FREE: 'Gratuit', PREMIUM: 'Premium', PRO: 'Pro' };

/** Ce qu'apporte chaque offre ; aucun prix tant qu'ils ne sont pas décidés (BILLING-01). */
export const PLAN_DESCRIPTIONS: Record<Plan, string> = {
  FREE: 'Carte, liste et fiches : la découverte reste gratuite.',
  PREMIUM: 'Personnalisation de l’expérience. Avantages à venir.',
  PRO: 'Espace professionnel : gérer vos lieux et vos food trucks, leurs emplacements et leur planning.',
};

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Envoyée',
  IN_REVIEW: 'En examen',
  INFO_REQUESTED: 'Informations demandées',
  APPROVED: 'Approuvée',
  REJECTED: 'Refusée',
  WITHDRAWN: 'Retirée',
};

export const PLACE_STATUS_LABELS: Record<ProPlaceStatus, string> = {
  DRAFT: 'Brouillon',
  PENDING: 'En attente de modération',
  PUBLISHED: 'Publié',
  REJECTED: 'Refusé',
  ARCHIVED: 'Archivé',
};

export const TRUCK_STATUS_LABELS: Record<TruckStatus, string> = {
  DRAFT: 'Brouillon',
  PENDING: 'En attente',
  PUBLISHED: 'Publié',
  SUSPENDED: 'Suspendu',
  ARCHIVED: 'Archivé',
  REJECTED: 'Refusé',
};

export const PRESENCE_STATUS_LABELS: Record<PresenceStatus, string> = {
  DRAFT: 'Brouillon',
  PUBLISHED: 'Publié',
  SUSPENDED: 'Désactivé',
  ARCHIVED: 'Archivé',
};

export const DAY_OPTIONS = [
  { value: 'MO', label: 'Lun' },
  { value: 'TU', label: 'Mar' },
  { value: 'WE', label: 'Mer' },
  { value: 'TH', label: 'Jeu' },
  { value: 'FR', label: 'Ven' },
  { value: 'SA', label: 'Sam' },
  { value: 'SU', label: 'Dim' },
] as const;

/** Une demande de gestion peut être complétée (brouillon, informations demandées). */
export function isClaimEditable(status: ClaimStatus): boolean {
  return status === 'DRAFT' || status === 'INFO_REQUESTED';
}

export function isClaimWithdrawable(status: ClaimStatus): boolean {
  return status === 'DRAFT' || status === 'SUBMITTED' || status === 'INFO_REQUESTED';
}

/** Actions possibles du propriétaire sur un food truck (une suspension par LocalLife bloque la publication). */
export function truckActions(status: TruckStatus, suspendedByLocalLife: boolean): OwnerAction[] {
  if (status === 'ARCHIVED') return [];
  const actions: OwnerAction[] = [];
  if ((status === 'DRAFT' || status === 'SUSPENDED') && !suspendedByLocalLife) actions.push('publish');
  if (status === 'PUBLISHED') actions.push('suspend');
  actions.push('archive');
  return actions;
}

/** Actions possibles sur un emplacement : publier (ou réactiver), désactiver, archiver. */
export function presenceActions(status: PresenceStatus): OwnerAction[] {
  if (status === 'ARCHIVED') return [];
  return [status === 'PUBLISHED' ? 'suspend' : 'publish', 'archive'];
}

/** Heure saisie `H:MM` ou `HH:MM` → `HH:MM` ; vide → `null` ; invalide → `undefined`. */
export function normalizeTime(value: string): string | null | undefined {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  const match = /^(\d{1,2})[:h](\d{2})$/.exec(trimmed);
  if (!match) return undefined;
  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (hours > 23 || minutes > 59) return undefined;
  return `${String(hours).padStart(2, '0')}:${match[2]}`;
}

/** Date saisie `AAAA-MM-JJ` ou `JJ/MM/AAAA` → `AAAA-MM-JJ` ; vide → `null` ; invalide → `undefined`. */
export function normalizeDate(value: string): string | null | undefined {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(trimmed);
  const french = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(trimmed);
  const [year, month, day] = iso ? [iso[1], iso[2], iso[3]] : french ? [french[3], french[2], french[1]] : [];
  if (!year) return undefined;
  const date = new Date(`${year}-${month}-${day}T12:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.getUTCDate() !== Number(day)) return undefined;
  return `${year}-${month}-${day}`;
}

export function formatDay(value: string | null): string {
  return value ? new Date(value).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' }) : '';
}

/** Phrase d'état de l'abonnement : offre, échéance ou résiliation. */
export function describeSubscription(overview: SubscriptionOverview): string {
  const { current, plan } = overview;
  if (!current || plan === 'FREE') return `Offre ${PLAN_LABELS[plan]}.`;
  if (current.status === 'CANCELED' && current.accessEndsAt) {
    return `Offre ${PLAN_LABELS[plan]} résiliée : accès conservé jusqu’au ${formatDay(current.accessEndsAt)}.`;
  }
  return current.accessEndsAt
    ? `Offre ${PLAN_LABELS[plan]} jusqu’au ${formatDay(current.accessEndsAt)} inclus.`
    : `Offre ${PLAN_LABELS[plan]}, sans échéance.`;
}
