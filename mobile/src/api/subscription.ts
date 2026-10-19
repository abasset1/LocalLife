import { request } from './http';
import type { CallOptions } from './activities';

/** Offre LocalLife (`Plan` backend — BILLING-01, ADR-0014). */
export type Plan = 'FREE' | 'PREMIUM' | 'PRO';

export type SubscriptionStatus =
  | 'REQUESTED'
  | 'ACTIVE'
  | 'CANCELED'
  | 'EXPIRED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'REPLACED';

export interface Subscription {
  id: number;
  plan: Exclude<Plan, 'FREE'>;
  status: SubscriptionStatus;
  startedAt: string | null;
  /** Fin d'accès ; `null` pour un abonnement sans échéance. */
  accessEndsAt: string | null;
  cancelAtPeriodEnd: boolean;
  decisionReason: string | null;
  requestedAt: string;
  updatedAt: string;
}

/** `GET /users/me/subscription`, partagé avec le site web. */
export interface SubscriptionOverview {
  plan: Plan;
  current: Subscription | null;
  pending: Subscription | null;
  history: Subscription[];
}

const BASE = '/api/v1/users/me/subscription';

export function getSubscription(options: CallOptions = {}): Promise<SubscriptionOverview> {
  return request<SubscriptionOverview>(BASE, { authenticated: true, signal: options.signal });
}

/** Demande de passage à Premium ou Pro, activée par l'équipe LocalLife (pas de paiement pour l'instant). */
export function requestPlan(plan: Exclude<Plan, 'FREE'>): Promise<SubscriptionOverview> {
  return request<SubscriptionOverview>(`${BASE}/request`, { method: 'POST', body: { plan }, authenticated: true });
}

export function withdrawPlanRequest(): Promise<SubscriptionOverview> {
  return request<SubscriptionOverview>(`${BASE}/withdraw`, { method: 'POST', authenticated: true });
}

/** Résiliation : l'accès dure jusqu'à `accessEndsAt`, ou s'arrête tout de suite sans échéance. */
export function cancelSubscription(): Promise<SubscriptionOverview> {
  return request<SubscriptionOverview>(`${BASE}/cancel`, { method: 'POST', authenticated: true });
}
