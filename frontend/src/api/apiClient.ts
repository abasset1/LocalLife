import { clearToken, getToken } from "../auth/authStorage";

/**
 * Wrapper autour de fetch pour les appels vers des endpoints protégés
 * (ex : POST /api/v1/activities) :
 * - ajoute automatiquement l'en-tête `Authorization: Bearer <token>` si un
 *   JWT est stocké ;
 * - en cas de réponse 401 (JWT manquant, invalide ou expiré), vide le
 *   storage et redirige vers /login.
 *
 * ⚠️ À utiliser uniquement pour les appels qui nécessitent une authentification.
 * Le JwtFilter backend renvoie 401 dès qu'un en-tête Authorization Bearer
 * invalide/expiré est présent, même sur une route publique (ex : GET
 * /api/v1/activities) : envoyer systématiquement le JWT sur les appels
 * publics casserait la lecture publique si le token est expiré. Les lectures
 * publiques doivent donc continuer à utiliser `fetch` directement.
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
    const token = getToken();
    const headers = new Headers(init.headers);

    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(input, { ...init, headers });

    if (response.status === 401) {
        clearToken();
        if (window.location.pathname !== "/login") {
            window.location.assign("/login");
        }
    }

    return response;
}
