import { useEffect, useState } from "react";
import type { ApiErrorBody } from "./types";
import { buildSearchUrl } from "./globalSearch";
import type { GlobalSearchPage, GlobalSearchRequest } from "./globalSearch";

export type ActivitySearchState =
    | { status: "idle" }
    | { status: "loading" }
    | { status: "success"; page: GlobalSearchPage; likedIds: ReadonlySet<number> }
    | { status: "error"; message: string };

const NO_LIKES: ReadonlySet<number> = new Set();

/** Ids des activités aimées : la recherche ne les renvoie pas. Sans session ou en cas d'échec, aucun. */
async function loadLikedIds(token: string | null, signal: AbortSignal): Promise<ReadonlySet<number>> {
    if (!token) {
        return NO_LIKES;
    }
    try {
        const response = await fetch("/api/v1/users/me/likes", {
            headers: { Authorization: `Bearer ${token}` },
            signal,
        });
        if (!response.ok) {
            return NO_LIKES;
        }
        const liked = (await response.json()) as Array<{ id: number }>;
        return new Set(liked.map((activity) => activity.id));
    } catch {
        return NO_LIKES;
    }
}

/**
 * Recherche globale (#92) : `GET /api/v1/activities/search` pour `request`, `null` = pas de recherche.
 * Chaque nouvelle demande (texte, filtre, position, page, `retryKey`) annule la précédente ; une réponse
 * arrivée malgré l'annulation est ignorée, seule la dernière demande peut afficher ses résultats.
 */
export function useActivitySearch(
    request: GlobalSearchRequest | null,
    token: string | null,
    retryKey = 0,
): ActivitySearchState {
    const url = request ? buildSearchUrl(request) : null;
    // État rattaché à sa demande : jusqu'à ce que l'effet démarre la nouvelle, l'ancien résultat n'est
    // jamais rendu pour une demande différente (voir le retour en bas).
    const [settled, setSettled] = useState<{ key: string; state: ActivitySearchState } | null>(null);
    const key = `${url}#${retryKey}`;

    useEffect(() => {
        if (url === null) {
            setSettled(null);
            return;
        }
        const abortController = new AbortController();
        const { signal } = abortController;
        const setState = (state: ActivitySearchState) => setSettled({ key, state });

        (async () => {
            const likedIdsPromise = loadLikedIds(token, signal);
            try {
                const response = await fetch(url, { signal });
                if (signal.aborted) {
                    return;
                }
                if (!response.ok) {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    if (!signal.aborted) {
                        setState({ status: "error", message: body?.message ?? "La recherche a échoué, réessaie." });
                    }
                    return;
                }
                const page = (await response.json()) as GlobalSearchPage;
                const likedIds = await likedIdsPromise;
                if (!signal.aborted) {
                    setState({ status: "success", page, likedIds });
                }
            } catch {
                if (!signal.aborted) {
                    setState({ status: "error", message: "Impossible de contacter le serveur, réessaie plus tard." });
                }
            }
        })();

        return () => abortController.abort();
    }, [key, token]);

    if (url === null) {
        return { status: "idle" };
    }
    return settled?.key === key ? settled.state : { status: "loading" };
}
