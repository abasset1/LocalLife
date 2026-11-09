import { clearSessionLocal, readCsrfToken } from "../auth/authStorage";

const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);
/** En-tête posé par le serveur sur un refus CSRF (`SecurityConfig.CSRF_REJECTED_HEADER`). */
const CSRF_REJECTED_HEADER = "X-CSRF-Rejected";
/** Lecture authentifiée légère, qui pose le cookie `XSRF-TOKEN` s'il manque. */
const CSRF_PRIME_URL = "/api/v1/users/me";

/**
 * Wrapper autour de fetch pour les appels authentifiés (correctif sécurité du 06/11/2026).
 *
 * - envoie le cookie de session `HttpOnly` automatiquement (`credentials: "include"`) : plus de jeton manipulé en
 *   JavaScript, donc rien à voler via une XSS ;
 * - ajoute l'en-tête anti-CSRF `X-XSRF-TOKEN` (recopié du cookie `XSRF-TOKEN`) sur les écritures
 *   (`POST/PUT/PATCH/DELETE`) — le serveur exige ce double justificatif pour toute écriture par cookie. Cookie
 *   absent : il est d'abord redemandé (`GET /users/me`) ; jeton refusé (403 avec `X-CSRF-Rejected`) : un nouveau
 *   jeton est obtenu et l'écriture rejouée une fois (sinon un administrateur lisait « rôle insuffisant », 07/11/2026) ;
 * - en cas de 401 (session absente, expirée, ou invalidée après un changement de mot de passe), efface le cache
 *   de session et redirige vers /login.
 *
 * ⚠️ Réservé aux appels qui exigent une authentification. Les lectures publiques peuvent utiliser `fetch`
 * directement : le cookie, s'il est présent et valide, authentifie l'appel ; s'il est absent ou invalide, le
 * backend répond en visiteur sur une route publique (il ne force plus un 401 sur une route publique quand le
 * jeton vient du cookie — voir `JwtFilter`).
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
    const method = (init.method ?? "GET").toUpperCase();
    const isWrite = !SAFE_METHODS.has(method);
    if (isWrite && !readCsrfToken()) {
        // Cookie anti-CSRF absent (jamais reçu, ou effacé alors que la session est restée) : sans lui, le serveur
        // refuse toute écriture par cookie. Une lecture authentifiée le fait poser.
        await primeCsrfToken();
    }

    let response = await send(input, init, isWrite);
    if (isWrite && response.status === 403 && response.headers.get(CSRF_REJECTED_HEADER) === "true") {
        // Jeton refusé (absent ou périmé) : on en obtient un nouveau et on rejoue l'écriture une seule fois.
        await primeCsrfToken();
        response = await send(input, init, isWrite);
    }

    if (response.status === 401) {
        clearSessionLocal();
        if (window.location.pathname !== "/login") {
            window.location.assign("/login");
        }
    }

    return response;
}

async function send(input: string, init: RequestInit, isWrite: boolean): Promise<Response> {
    const headers = new Headers(init.headers);
    if (isWrite) {
        const csrfToken = readCsrfToken();
        if (csrfToken) {
            headers.set(CSRF_HEADER, csrfToken);
        }
    }
    return fetch(input, { ...init, headers, credentials: "include" });
}

/** Fait poser le cookie `XSRF-TOKEN` par le serveur (toute réponse authentifiée le pose s'il manque). */
async function primeCsrfToken(): Promise<void> {
    try {
        await fetch(CSRF_PRIME_URL, { credentials: "include" });
    } catch {
        // Réseau indisponible : l'écriture qui suit échouera d'elle-même, avec son propre message.
    }
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
