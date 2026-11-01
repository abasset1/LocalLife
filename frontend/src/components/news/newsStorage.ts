/**
 * Mémoire locale des annonces de la nouveauté « Suivre », par compte et par appareil : bandeau fermé, popup déjà
 * montrée. Stockage indisponible (navigation privée…) : rien n'est mémorisé, l'annonce vaut pour la session.
 */
export function followNewsDismissKey(userId: number): string {
    return `locallife.news.follow.dismissed.${userId}`;
}

export function followNewsPopupKey(userId: number): string {
    return `locallife.news.follow.popupSeen.${userId}`;
}

export function readFlag(key: string): boolean {
    try {
        return window.localStorage.getItem(key) === "1";
    } catch {
        return false;
    }
}

export function writeFlag(key: string) {
    try {
        window.localStorage.setItem(key, "1");
    } catch {
        // Stockage indisponible : l'annonce n'est pas mémorisée.
    }
}
