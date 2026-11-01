import { useEffect, useState } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import { getPayload } from "../../auth/authStorage";

export type FollowTargetType = "activity" | "place";

/** Activité ou lieu suivi (`GET /api/v1/users/me/follows`). */
export interface FollowedItem {
    type: FollowTargetType;
    id: number;
    title: string | null;
    city: string | null;
    /** Statut de l'activité (`PUBLISHED`, `CANCELLED`…) ou du lieu (`PUBLISHED`, `ARCHIVED`…). */
    status: string | null;
    followedAt: string;
}

export interface FollowResult {
    ok: boolean;
    message?: string;
    /** 403 : réservé aux offres Premium et Pro. */
    isPlanLimit?: boolean;
}

/**
 * Suivis de l'utilisateur connecté, chargés une fois et partagés par toutes les cloches de la page : suivre ou ne plus
 * suivre depuis une fiche met aussitôt à jour les autres (fiche, liste du profil). Rechargés au changement de compte.
 */
let state: { userId: number | null; items: FollowedItem[] | null; loading: Promise<void> | null } = {
    userId: null,
    items: null,
    loading: null,
};
const listeners = new Set<() => void>();

function notify() {
    listeners.forEach((listener) => listener());
}

function path(type: FollowTargetType, id: number): string {
    return `/api/v1/${type === "activity" ? "activities" : "places"}/${id}/follow`;
}

/** Charge les suivis du compte connecté (une seule requête à la fois). */
export function loadFollows(force = false): Promise<void> {
    const userId = getPayload()?.userId ?? null;
    if (userId === null) {
        state = { userId: null, items: [], loading: null };
        notify();
        return Promise.resolve();
    }
    if (!force && state.userId === userId && (state.items !== null || state.loading)) {
        return state.loading ?? Promise.resolve();
    }
    const loading = apiFetch("/api/v1/users/me/follows")
        .then(async (response) => (response.ok ? ((await response.json()) as FollowedItem[]) : []))
        .catch(() => [] as FollowedItem[])
        .then((items) => {
            if (state.loading === loading) {
                state = { userId, items, loading: null };
                notify();
            }
        });
    state = { userId, items: state.userId === userId ? state.items : null, loading };
    return loading;
}

export function isFollowing(type: FollowTargetType, id: number): boolean {
    return state.items?.some((item) => item.type === type && item.id === id) ?? false;
}

/** Suit ou ne suit plus une activité ou un lieu ; met à jour les suivis partagés en cas de succès. */
export async function setFollowing(
    type: FollowTargetType, id: number, follow: boolean, label: { title: string; city?: string | null },
): Promise<FollowResult> {
    try {
        const response = await apiFetch(path(type, id), { method: follow ? "POST" : "DELETE" });
        if (response.status === 403) {
            const body = (await response.json().catch(() => null)) as { message?: string } | null;
            return {
                ok: false,
                isPlanLimit: true,
                message: body?.message ?? "Suivre une activité ou un lieu est réservé aux offres Premium et Pro.",
            };
        }
        if (!response.ok) {
            return {
                ok: false,
                message: await describeErrorResponse(response, follow
                    ? "Impossible de suivre pour le moment"
                    : "Impossible d'arrêter le suivi pour le moment"),
            };
        }
        const others = (state.items ?? []).filter((item) => !(item.type === type && item.id === id));
        state = {
            ...state,
            items: follow
                ? [{ type, id, title: label.title, city: label.city ?? null, status: "PUBLISHED",
                    followedAt: new Date().toISOString() }, ...others]
                : others,
        };
        notify();
        return { ok: true };
    } catch {
        return { ok: false, message: "Impossible de contacter le serveur, réessaie plus tard." };
    }
}

/** Suivis du compte connecté, rafraîchis à chaque changement ; `null` pendant le premier chargement. */
export function useFollows(): FollowedItem[] | null {
    const [, setVersion] = useState(0);
    useEffect(() => {
        const listener = () => setVersion((version) => version + 1);
        listeners.add(listener);
        void loadFollows();
        return () => {
            listeners.delete(listener);
        };
    }, []);
    return state.userId === (getPayload()?.userId ?? null) ? state.items : null;
}

/** Pour les tests : oublie les suivis chargés. */
export function resetFollowsForTests() {
    state = { userId: null, items: null, loading: null };
}
