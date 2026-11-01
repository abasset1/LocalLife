import { useState } from "react";
import { Notice, ProfileCard } from "../profile/ProfileUi";
import { setFollowing, useFollows } from "./followStore";
import type { FollowedItem } from "./followStore";

const STATUS_LABELS: Record<string, string> = {
    CANCELLED: "annulée",
    ARCHIVED: "terminée",
    DELETED: "retirée",
};

function statusLabel(item: FollowedItem): string | null {
    if (item.type === "place") {
        return item.status && item.status !== "PUBLISHED" ? "retiré de la carte" : null;
    }
    return item.status ? STATUS_LABELS[item.status] ?? null : null;
}

/**
 * « Activités et lieux suivis » (Mon profil) : ce que l'utilisateur suit avec la cloche, et « Ne plus suivre ». Le
 * suivi est réservé aux offres Premium et Pro ; un compte revenu au gratuit garde sa liste mais n'est plus prévenu.
 */
function FollowedItemsCard({ canFollow }: { canFollow: boolean }) {
    const follows = useFollows();
    const [busyKey, setBusyKey] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    async function unfollow(item: FollowedItem) {
        const key = `${item.type}-${item.id}`;
        setBusyKey(key);
        setError(null);
        const result = await setFollowing(item.type, item.id, false, { title: item.title ?? "" });
        setBusyKey(null);
        if (!result.ok) {
            setError(result.message ?? "Erreur inattendue.");
        }
    }

    return (
        <ProfileCard
            icon="🔔"
            subtitle="Tu es prévenu de leurs mises à jour : dates, lieu, annulation, nouvelles activités d'un lieu."
            title="Activités et lieux suivis"
            titleId="profile-follows-title"
        >
            {!canFollow && (
                <Notice kind="info">
                    Suivre une activité ou un lieu est réservé aux offres Premium et Pro.
                    {follows && follows.length > 0 && " Tes suivis restent listés, mais tu n'es plus prévenu."}
                </Notice>
            )}
            {follows === null && <p className="pp-hint">Chargement…</p>}
            {follows !== null && follows.length === 0 && canFollow && (
                <p className="pp-hint">
                    Rien pour l'instant : touche 🔔 « Suivre » sur la fiche d'une activité ou d'un lieu.
                </p>
            )}
            {follows !== null && follows.length > 0 && (
                <ul className="follow-list">
                    {follows.map((item) => {
                        const key = `${item.type}-${item.id}`;
                        const status = statusLabel(item);
                        return (
                            <li className="follow-list-item" key={key}>
                                <span aria-hidden="true">{item.type === "activity" ? "📅" : "🏛"}</span>
                                <span className="follow-list-text">
                                    <strong>{item.title ?? "Sans titre"}</strong>
                                    <small>
                                        {item.type === "activity" ? "Activité" : "Lieu"}
                                        {item.city ? ` · ${item.city}` : ""}
                                        {status ? ` · ${status}` : ""}
                                    </small>
                                </span>
                                <button
                                    className="pp-button pp-button-secondary"
                                    disabled={busyKey === key}
                                    onClick={() => void unfollow(item)}
                                    type="button"
                                >
                                    Ne plus suivre
                                </button>
                            </li>
                        );
                    })}
                </ul>
            )}
            {error && <Notice kind="error">{error}</Notice>}
        </ProfileCard>
    );
}

export default FollowedItemsCard;
