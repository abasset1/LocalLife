import type { SubscriptionOverview } from '@/api';

import { describeSubscription, normalizeDate, normalizeTime, presenceActions, truckActions } from '../format';

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
    expect(truckActions('DRAFT', false)).toEqual(['publish', 'archive']);
    expect(truckActions('PUBLISHED', false)).toEqual(['suspend', 'archive']);
    expect(truckActions('SUSPENDED', true)).toEqual(['archive']);
    expect(truckActions('ARCHIVED', false)).toEqual([]);
    expect(presenceActions('SUSPENDED')).toEqual(['publish', 'archive']);
    expect(presenceActions('PUBLISHED')).toEqual(['suspend', 'archive']);
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
