import type { SubscriptionOverview } from '@/api';

import {
  TRUCK_STATUS_LABELS,
  describeSubscription,
  hostablePlaces,
  normalizeDate,
  normalizeTime,
  presenceActions,
  truckActionLabel,
  truckActions,
  truckStatusLabel,
} from '../format';

const truck = { status: 'DRAFT' as const, suspendedByLocalLife: false, approved: true, suspendedForAccess: false };

describe('espace professionnel : règles d’affichage', () => {
  it('normalise les heures saisies et refuse les heures impossibles', () => {
    expect(normalizeTime('8:00')).toBe('08:00');
    expect(normalizeTime('18h30')).toBe('18:30');
    expect(normalizeTime('')).toBeNull();
    expect(normalizeTime('25:00')).toBeUndefined();
    expect(normalizeTime('midi')).toBeUndefined();
  });

  it('accepte les dates françaises ou ISO, refuse les dates impossibles', () => {
    expect(normalizeDate('24/10/2026')).toBe('2026-10-24');
    expect(normalizeDate('2026-10-24')).toBe('2026-10-24');
    expect(normalizeDate(' ')).toBeNull();
    expect(normalizeDate('31/02/2026')).toBeUndefined();
  });

  it('propose les seules actions permises sur un food truck et un emplacement', () => {
    expect(truckActions(truck)).toEqual(['publish', 'archive']);
    expect(truckActions({ ...truck, status: 'PUBLISHED' })).toEqual(['suspend', 'archive']);
    expect(truckActions({ ...truck, status: 'SUSPENDED', suspendedByLocalLife: true })).toEqual(['archive']);
    expect(truckActions({ ...truck, status: 'ARCHIVED' })).toEqual([]);
    expect(presenceActions('SUSPENDED')).toEqual(['publish', 'archive']);
    expect(presenceActions('PUBLISHED')).toEqual(['suspend', 'archive']);
  });

  it('envoie en validation un food truck jamais validé, retire la demande en attente', () => {
    const fresh = { ...truck, approved: false };
    expect(truckActions(fresh)).toEqual(['publish', 'archive']);
    expect(truckActionLabel('publish', fresh)).toBe('Envoyer en validation');
    expect(truckActionLabel('publish', truck)).toBe('Publier');
    expect(truckActions({ ...fresh, status: 'PENDING' })).toEqual(['suspend', 'archive']);
    expect(truckActionLabel('suspend', { ...fresh, status: 'PENDING' })).toBe('Retirer la demande');
    expect(truckActionLabel('suspend', { ...truck, status: 'PUBLISHED' })).toBe('Suspendre');
    expect(truckActions({ ...fresh, status: 'REJECTED' })).toEqual(['publish', 'archive']);
    // Validé une fois : un refus n'est plus possible, la suspension se republie directement.
    expect(truckActions({ ...truck, status: 'SUSPENDED' })).toEqual(['publish', 'archive']);
    // Masqué faute d'accès pro : republié automatiquement au retour de l'accès, seul l'archivage reste.
    expect(truckActions({ ...truck, status: 'SUSPENDED', suspendedForAccess: true })).toEqual(['archive']);
  });

  it('affiche les statuts de food truck, dont le masquage faute d’accès pro', () => {
    expect(TRUCK_STATUS_LABELS.PENDING).toBe('En validation');
    expect(TRUCK_STATUS_LABELS.REJECTED).toBe('Refusé');
    expect(truckStatusLabel({ status: 'SUSPENDED', suspendedForAccess: false })).toBe('Suspendu');
    expect(truckStatusLabel({ status: 'SUSPENDED', suspendedForAccess: true })).toBe(
      'Masqué : votre accès professionnel a pris fin',
    );
  });

  it('ne propose comme « Proposé par » que les lieux gérés publiés', () => {
    const place = {
      id: 1, name: 'Le Comptoir', placeType: 'Restaurant', status: 'PUBLISHED' as const, address: null,
      postalCode: null, city: null, description: null, website: null, phone: null, email: null,
      origin: 'IMPORTED' as const, editable: false, changeProposable: true,
    };
    expect(hostablePlaces([place, { ...place, id: 2, status: 'PENDING' }]).map((p) => p.id)).toEqual([1]);
  });

  it('explique l’offre et la date de fin d’accès', () => {
    const base: SubscriptionOverview = { plan: 'FREE', current: null, pending: null, history: [] };
    expect(describeSubscription(base)).toBe('Offre Gratuit.');
    const current = {
      id: 1, plan: 'PRO' as const, status: 'ACTIVE' as const, startedAt: null, accessEndsAt: null,
      cancelAtPeriodEnd: false, decisionReason: null, requestedAt: '', updatedAt: '',
    };
    expect(describeSubscription({ ...base, plan: 'PRO', current })).toBe('Offre Pro, sans échéance.');
    expect(describeSubscription({
      ...base, plan: 'PRO', current: { ...current, status: 'CANCELED', accessEndsAt: '2026-11-30T23:00:00Z' },
    })).toMatch(/^Offre Pro résiliée : accès conservé jusqu’au /);
  });
});
