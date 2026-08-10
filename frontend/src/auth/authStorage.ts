const TOKEN_KEY = "locallife.jwt";

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
