const TOKEN_KEY = "locallife.jwt";

interface JwtPayload {
    userId: number;
    email: string;
    role: string;
    exp?: number;
}

/**
 * Stocke le JWT après une connexion réussie.
 * localStorage est utilisé plutôt que sessionStorage pour que la session
 * survive à la fermeture de l'onglet (choix simple pour le MVP, sans
 * "se souvenir de moi").
 */
export function saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
}

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
    return getToken() !== null;
}

/**
 * Décode le payload du JWT stocké, pour affichage uniquement (ex : email
 * de l'utilisateur connecté dans l'en-tête). Ce n'est PAS une vérification
 * de signature — la sécurité réelle reste entièrement côté backend, qui
 * revalide chaque token à chaque requête protégée (JwtFilter).
 */
export function getPayload(): JwtPayload | null {
    const token = getToken();

    if (!token) {
        return null;
    }

    try {
        const payloadSegment = token.split(".")[1];
        const normalized = payloadSegment.replace(/-/g, "+").replace(/_/g, "/");
        return JSON.parse(atob(normalized)) as JwtPayload;
    } catch {
        return null;
    }
}
