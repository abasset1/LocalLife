import type {
  ClaimStatus,
  ManagedPlace,
  OwnedFoodTruck,
  OwnerAction,
  PlaceChangeStatus,
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

export const PLACE_CHANGE_STATUS_LABELS: Record<PlaceChangeStatus, string> = {
  PENDING: 'En attente de validation',
  APPROVED: 'Validée',
  REJECTED: 'Refusée',
  WITHDRAWN: 'Retirée',
};

export const TRUCK_STATUS_LABELS: Record<TruckStatus, string> = {
  DRAFT: 'Brouillon',
  PENDING: 'En validation',
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

type TruckState = Pick<OwnedFoodTruck, 'status' | 'suspendedByLocalLife' | 'approved' | 'suspendedForAccess'>;

/** Statut affiché d'un food truck : un food truck masqué faute d'accès pro le dit plutôt que « Suspendu ». */
export function truckStatusLabel(truck: Pick<OwnedFoodTruck, 'status' | 'suspendedForAccess'>): string {
  if (truck.status === 'SUSPENDED' && truck.suspendedForAccess) return 'Masqué : votre accès professionnel a pris fin';
  return TRUCK_STATUS_LABELS[truck.status];
}

/**
 * Actions possibles du propriétaire sur un food truck. Jamais validé, « publier » l'envoie en validation (brouillon
 * ou refusé → en validation) ; « suspendre » une demande en validation la retire (retour en brouillon). Une
 * suspension par LocalLife bloque la publication ; un food truck masqué faute d'accès pro revient tout seul.
 */
export function truckActions(truck: TruckState): OwnerAction[] {
  const { status, suspendedByLocalLife, approved, suspendedForAccess } = truck;
  if (status === 'ARCHIVED') return [];
  const actions: OwnerAction[] = [];
  const publishable = approved ? status === 'DRAFT' || status === 'SUSPENDED' : status === 'DRAFT' || status === 'REJECTED';
  if (publishable && !suspendedByLocalLife && !suspendedForAccess) actions.push('publish');
  if (status === 'PUBLISHED' || status === 'PENDING') actions.push('suspend');
  actions.push('archive');
  return actions;
}

/** Libellé d'une action sur un food truck, selon qu'il a déjà été validé ou attend sa validation. */
export function truckActionLabel(action: OwnerAction, truck: Pick<OwnedFoodTruck, 'status' | 'approved'>): string {
  if (action === 'publish') return truck.approved ? 'Publier' : 'Envoyer en validation';
  if (action === 'suspend') return truck.status === 'PENDING' ? 'Retirer la demande' : 'Suspendre';
  return 'Archiver';
}

/** Actions possibles sur un emplacement : publier (ou réactiver), désactiver, archiver. */
export function presenceActions(status: PresenceStatus): OwnerAction[] {
  if (status === 'ARCHIVED') return [];
  return [status === 'PUBLISHED' ? 'suspend' : 'publish', 'archive'];
}

/** Lieux qu'un compte peut indiquer comme « Proposé par » d'une activité : ceux qu'il gère et qui sont publiés. */
export function hostablePlaces(places: readonly ManagedPlace[]): ManagedPlace[] {
  return places.filter((place) => place.status === 'PUBLISHED');
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
