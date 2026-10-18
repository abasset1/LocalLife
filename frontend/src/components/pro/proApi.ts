import { apiFetch } from "../../api/apiClient";

/** Résultat d'un appel à l'espace professionnel : le corps JSON, ou le message d'erreur du backend. */
export type ProResult<T> = { ok: true; data: T } | { ok: false; status: number; message: string; body: unknown };

/** Appel JSON authentifié vers `/api/v1/pro/**` ou `/api/v1/admin/**` (ADR-0012). */
export async function proRequest<T>(url: string, method = "GET", body?: unknown): Promise<ProResult<T>> {
    try {
        const response = await apiFetch(url, {
            method,
            headers: body === undefined ? undefined : { "Content-Type": "application/json" },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
        const json = (await response.json().catch(() => null)) as unknown;
        if (response.ok) {
            return { ok: true, data: json as T };
        }
        const message = (json as { message?: string } | null)?.message ?? "Action impossible, réessaie.";
        return { ok: false, status: response.status, message, body: json };
    } catch {
        return { ok: false, status: 0, message: "Impossible de contacter le serveur, réessaie plus tard.", body: null };
    }
}

export function formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString("fr-FR", { dateStyle: "medium" }) : "";
}
