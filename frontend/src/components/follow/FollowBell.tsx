import { useState } from "react";
import { Link } from "react-router-dom";
import { getPayload } from "../../auth/authStorage";
import { isFollowing, setFollowing, useFollows } from "./followStore";
import type { FollowTargetType } from "./followStore";

interface FollowBellProps {
    type: FollowTargetType;
    id: number;
    title: string;
    city?: string | null;
}

/**
 * Cloche « Suivre » : être prévenu des mises à jour d'une activité (dates, lieu, annulation…) ou d'un lieu (nouvelles
 * activités, adresse, horaires…). Réservée aux offres Premium et Pro : pour un compte gratuit, le refus du serveur est
 * affiché avec un lien vers les offres. Sans connexion, la cloche n'est pas affichée.
 */
function FollowBell({ type, id, title, city }: FollowBellProps) {
    const follows = useFollows();
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<{ message: string; isPlanLimit: boolean } | null>(null);

    if (!getPayload()) {
        return null;
    }

    const following = follows !== null && isFollowing(type, id);
    const what = type === "activity" ? "cette activité" : "ce lieu";

    async function toggle() {
        setIsBusy(true);
        setError(null);
        const result = await setFollowing(type, id, !following, { title, city });
        setIsBusy(false);
        if (!result.ok) {
            setError({ message: result.message ?? "Erreur inattendue.", isPlanLimit: result.isPlanLimit ?? false });
        }
    }

    return (
        <div className="follow-bell">
            <button
                aria-label={following ? `Ne plus suivre ${what}` : `Suivre ${what}`}
                aria-pressed={following}
                className={following ? "follow-bell-button follow-bell-button-active" : "follow-bell-button"}
                disabled={isBusy || follows === null}
                onClick={() => void toggle()}
                title={following
                    ? `Tu seras prévenu des mises à jour de ${what}`
                    : `Être prévenu des mises à jour de ${what} (Premium et Pro)`}
                type="button"
            >
                <span aria-hidden="true">🔔</span>
                {following ? "Suivi ✓" : "Suivre"}
            </button>
            {error && (
                <p className="follow-bell-error" role="alert">
                    {error.message}
                    {error.isPlanLimit && <> <Link to="/profile">Voir les offres</Link></>}
                </p>
            )}
        </div>
    );
}

export default FollowBell;
