import { clearSessionLocal, readCsrfToken } from "../auth/authStorage";

const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

/**
 * Wrapper autour de fetch pour les appels authentifiés (correctif sécurité du 06/11/2026).
 *
 * - envoie le cookie de session `HttpOnly` automatiquement (`credentials: "include"`) : plus de jeton manipulé en
 *   JavaScript, donc rien à voler via une XSS ;
 * - ajoute l'en-tête anti-CSRF `X-XSRF-TOKEN` (recopié du cookie `XSRF-TOKEN`) sur les écritures
 *   (`POST/PUT/PATCH/DELETE`) — le serveur exige ce double justificatif pour toute écriture par cookie ;
 * - en cas de 401 (session absente, expirée, ou invalidée après un changement de mot de passe), efface le cache
 *   de session et redirige vers /login.
 *
 * ⚠️ Réservé aux appels qui exigent une authentification. Les lectures publiques peuvent utiliser `fetch`
 * directement : le cookie, s'il est présent et valide, authentifie l'appel ; s'il est absent ou invalide, le
 * backend répond en visiteur sur une route publique (il ne force plus un 401 sur une route publique quand le
 * jeton vient du cookie — voir `JwtFilter`).
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    const method = (init.method ?? "GET").toUpperCase();
    if (!SAFE_METHODS.has(method)) {
        const csrfToken = readCsrfToken();
        if (csrfToken) {
            headers.set(CSRF_HEADER, csrfToken);
        }
    }

    const response = await fetch(input, { ...init, headers, credentials: "include" });

    if (response.status === 401) {
        clearSessionLocal();
        if (window.location.pathname !== "/login") {
            window.location.assign("/login");
        }
    }

    return response;
}

/**
 * Message d'erreur lisible pour une réponse en échec : le contexte, puis la cause renvoyée par le backend
 * (`ErrorResponse.message`) et le code HTTP — plutôt qu'un message générique qui masque, par exemple, une route
 * absente d'un backend pas à jour (404 « Route introuvable »).
 */
export async function describeErrorResponse(response: Response, context: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    const cause = body?.message || response.statusText || "réponse inattendue du serveur";
    return `${context} : ${cause} (HTTP ${response.status}).`;
}
