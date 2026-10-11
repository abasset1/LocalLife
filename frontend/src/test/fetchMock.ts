import { vi } from "vitest";

/** Requête de suggestions interceptée : saisie envoyée, signal d'annulation et réponse à déclencher. */
export interface PendingRequest {
    query: string;
    signal: AbortSignal | undefined;
    /** Répond 200 avec ce corps JSON — même si la requête a été annulée entre-temps (réponse tardive). */
    respond: (body: unknown) => void;
    fail: () => void;
}

/**
 * Remplace `fetch` : chaque appel reste en attente jusqu'à `respond`/`fail`, dans l'ordre choisi par le
 * test. L'annulation n'interrompt volontairement pas la réponse : c'est le cas d'un `fetch` résolu
 * malgré l'annulation, que le code doit ignorer lui-même.
 */
export function installControllableFetch(): PendingRequest[] {
    const requests: PendingRequest[] = [];
    vi.stubGlobal(
        "fetch",
        vi.fn((input: string, init?: RequestInit) => {
            const query = new URL(input, "http://localhost").searchParams.get("q") ?? "";
            return new Promise<Response>((resolve, reject) => {
                requests.push({
                    query,
                    signal: init?.signal ?? undefined,
                    respond: (body) =>
                        resolve(new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } })),
                    fail: () => reject(new TypeError("Failed to fetch")),
                });
            });
        }),
    );
    return requests;
}
