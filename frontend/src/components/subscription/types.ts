/** Offre LocalLife (`Plan`, backend — BILLING-01, ADR-0014). */
export type Plan = "FREE" | "PREMIUM" | "PRO";

export type SubscriptionStatus =
    "REQUESTED" | "ACTIVE" | "CANCELED" | "EXPIRED" | "REJECTED" | "WITHDRAWN" | "REPLACED";

export interface Subscription {
    id: number;
    plan: Exclude<Plan, "FREE">;
    status: SubscriptionStatus;
    startedAt: string | null;
    /** Fin d'accès, `null` pour un abonnement sans échéance. */
    accessEndsAt: string | null;
    cancelAtPeriodEnd: boolean;
    decisionReason: string | null;
    requestedAt: string;
    updatedAt: string;
}

/** `GET /api/v1/users/me/subscription`. */
export interface SubscriptionOverview {
    plan: Plan;
    current: Subscription | null;
    pending: Subscription | null;
    history: Subscription[];
}

export const PLAN_LABELS: Record<Plan, string> = { FREE: "Gratuit", PREMIUM: "Premium", PRO: "Pro" };

/** Ce que chaque offre apporte ; aucun prix n'est affiché tant qu'ils ne sont pas décidés (BILLING-01). */
export const PLAN_DESCRIPTIONS: Record<Plan, string> = {
    FREE: "Carte, liste et fiches : la découverte reste gratuite.",
    PREMIUM: "Personnalisation de l'expérience et proposition d'activités.",
    PRO: "Espace professionnel : gérer tes lieux et tes food trucks, leurs emplacements et leur planning.",
};

/** Limites de chaque offre (décision du 22/10/2026), appliquées par le serveur. */
export const PLAN_LIMITS: Record<Plan, string[]> = {
    FREE: ["5 favoris", "1 alerte"],
    PREMIUM: ["Favoris illimités", "10 alertes", "2 contributions par mois"],
    PRO: ["Favoris illimités", "10 alertes", "Contributions illimitées", "Espace professionnel"],
};

export function formatDay(value: string | null): string {
    return value ? new Date(value).toLocaleDateString("fr-FR", { dateStyle: "long" }) : "";
}
