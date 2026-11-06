/**
 * Session de l'utilisateur connecté (correctif sécurité du 06/11/2026).
 *
 * <p>Le JWT n'est plus stocké dans `localStorage` (lisible par n'importe quel script, donc volable via une faille
 * XSS). Il vit désormais dans un cookie `HttpOnly` posé par le serveur à la connexion (voir `AuthCookie` côté
 * backend) : le navigateur l'envoie automatiquement, mais le JavaScript ne peut ni le lire ni l'exfiltrer.
 *
 * <p>Comme le jeton est illisible, l'identité et le rôle de l'utilisateur ne se décodent plus côté client : ils
 * proviennent de `GET /api/v1/users/me` (authentifié par le cookie) et sont gardés dans un cache mémoire. Les
 * écrans lisent ce cache de façon synchrone via {@link getPayload} ; il est alimenté au démarrage
 * ({@link refreshSession} dans `main.tsx`) et après chaque connexion ({@link onAuthenticated}).
 */

const ME_URL = "/api/v1/users/me";
const LOGOUT_URL = "/api/v1/auth/logout";

/**
 * Horodatage (secondes) de la dernière connexion, conservé en `localStorage`. Ce n'est PAS le jeton (anodin, non
 * sensible) : il remplace le claim `iat` du JWT, qui n'est plus lisible côté client, pour les écrans qui en ont
 * besoin (ex. proposition Premium « récemment connecté »). Posé à la connexion, pas au rechargement, il reste donc
 * stable tant que la session dure.
 */
const LOGIN_AT_KEY = "locallife.loginAt";

/** Identité de la session, dérivée de `GET /users/me`. `userId` reprend l'ancien nom du claim pour compatibilité. */
export interface SessionUser {
    userId: number;
    email: string;
    role: string;
    /** Heure de connexion (secondes), ex-claim `iat` ; `undefined` si inconnue. */
    iat?: number;
}

function readLoginAt(): number | undefined {
    try {
        const value = localStorage.getItem(LOGIN_AT_KEY);
        return value ? Number(value) : undefined;
    } catch {
        return undefined;
    }
}

function writeLoginAt(seconds: number): void {
    try {
        localStorage.setItem(LOGIN_AT_KEY, String(seconds));
    } catch {
        // Horodatage de confort : son absence masque juste la proposition « récemment connecté ».
    }
}

function clearLoginAt(): void {
    try {
        localStorage.removeItem(LOGIN_AT_KEY);
    } catch {
        // Rien à faire : la session est de toute façon considérée fermée.
    }
}

let currentUser: SessionUser | null = null;
const listeners = new Set<() => void>();

function notify(): void {
    for (const listener of listeners) {
        listener();
    }
}

/** S'abonne aux changements de session (connexion/déconnexion), pour les composants qui doivent se rafraîchir. */
export function subscribeSession(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

/**
 * Utilisateur de la session courante (cache synchrone), ou `null` pour un visiteur. Remplace l'ancien décodage du
 * JWT : même forme (`userId`/`email`/`role`), mais la donnée vient du serveur, pas d'un jeton lisible côté client.
 */
export function getPayload(): SessionUser | null {
    return currentUser;
}

/** Vrai si une session est ouverte (cache non vide). */
export function isAuthenticated(): boolean {
    return currentUser !== null;
}

function setUser(user: SessionUser | null): void {
    currentUser = user;
    notify();
}

/**
 * Relit la session depuis le cookie (`GET /users/me`) et met le cache à jour. Appelée au démarrage de
 * l'application et après une connexion. Ne redirige jamais : un échec signifie simplement « visiteur ».
 */
export async function refreshSession(): Promise<SessionUser | null> {
    try {
        const response = await fetch(ME_URL, { credentials: "include" });
        if (response.ok) {
            const me = (await response.json()) as { id: number; email: string; role: string };
            setUser({ userId: me.id, email: me.email, role: me.role, iat: readLoginAt() });
        } else {
            setUser(null);
        }
    } catch {
        setUser(null);
    }
    return currentUser;
}

/** Après une connexion réussie : le serveur vient de poser le cookie de session, on charge le profil. */
export async function onAuthenticated(): Promise<SessionUser | null> {
    writeLoginAt(Math.floor(Date.now() / 1000));
    return refreshSession();
}

/** Déconnexion : efface le cache local et demande au serveur d'effacer le cookie `HttpOnly` (seul à pouvoir le faire). */
export async function clearSession(): Promise<void> {
    clearLoginAt();
    setUser(null);
    try {
        await fetch(LOGOUT_URL, { method: "POST", credentials: "include" });
    } catch {
        // La déconnexion locale est faite quoi qu'il arrive ; le cookie expirera de lui-même en cas d'échec réseau.
    }
}

/** Efface seulement le cache local (réponse 401 : le cookie est déjà refusé côté serveur, rien à appeler). */
export function clearSessionLocal(): void {
    clearLoginAt();
    setUser(null);
}

/**
 * Valeur du cookie CSRF `XSRF-TOKEN` (posé par le serveur, lisible par le JS), à renvoyer dans l'en-tête
 * `X-XSRF-TOKEN` sur chaque écriture authentifiée par cookie — double soumission anti-CSRF (voir `apiFetch`).
 */
export function readCsrfToken(): string | null {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}
