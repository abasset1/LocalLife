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

/** « Ne plus me proposer » de la popup Premium : définitif, par compte et par appareil. */
export function premiumOfferOptOutKey(userId: number): string {
    return `locallife.premiumOffer.optOut.${userId}`;
}

/** Popup Premium fermée pendant cette visite (onglet) : elle revient à la visite suivante. */
export function premiumOfferClosedKey(userId: number): string {
    return `locallife.premiumOffer.closed.${userId}`;
}

export function readSessionFlag(key: string): boolean {
    try {
        return window.sessionStorage.getItem(key) === "1";
    } catch {
        return false;
    }
}

export function writeSessionFlag(key: string) {
    try {
        window.sessionStorage.setItem(key, "1");
    } catch {
        // Stockage indisponible : la fermeture vaut pour l'affichage en cours.
    }
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
