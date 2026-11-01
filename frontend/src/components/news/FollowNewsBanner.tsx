import { useState } from "react";
import { Link } from "react-router-dom";

/** Clé de fermeture du bandeau, par compte : chacun le ferme pour lui sur cet appareil. */
export function followNewsDismissKey(userId: number): string {
    return `locallife.news.follow.dismissed.${userId}`;
}

function readDismissed(userId: number): boolean {
    try {
        return window.localStorage.getItem(followNewsDismissKey(userId)) === "1";
    } catch {
        return false;
    }
}

function writeDismissed(userId: number) {
    try {
        window.localStorage.setItem(followNewsDismissKey(userId), "1");
    } catch {
        // Stockage indisponible (navigation privée…) : le bandeau reste fermé pour la session.
    }
}

/**
 * Bandeau de la carte qui annonce aux membres gratuits la cloche « Suivre » et l'offre Premium gratuite pour le
 * moment ; il mène à la page « Nouveautés ». Affiché seulement quand le serveur a confirmé que le compte n'a pas le
 * suivi (`canFollow === false`), jamais à un visiteur ni à un membre Premium ou Pro. Fermé (✕ ou « Découvrir »), il ne
 * revient plus pour ce compte sur cet appareil.
 */
function FollowNewsBanner({ userId, canFollow }: { userId: number | null; canFollow: boolean | null }) {
    const [dismissedFor, setDismissedFor] = useState<number | null>(null);

    if (userId === null || canFollow !== false || dismissedFor === userId || readDismissed(userId)) {
        return null;
    }

    function dismiss() {
        if (userId === null) {
            return;
        }
        writeDismissed(userId);
        setDismissedFor(userId);
    }

    return (
        <aside aria-label="Nouveauté" className="news-banner">
            <span aria-hidden="true" className="news-banner-bell">🔔</span>
            <p className="news-banner-text">
                <strong>Nouveau : suis tes activités et tes lieux préférés.</strong>{" "}
                <span>Premium est gratuit pour le moment.</span>
            </p>
            <Link className="news-banner-link" onClick={dismiss} to="/nouveautes">Découvrir</Link>
            <button aria-label="Fermer le bandeau" className="news-banner-close" onClick={dismiss} type="button">
                ✕
            </button>
        </aside>
    );
}

export default FollowNewsBanner;
