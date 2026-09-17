import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/apiClient";
import { getPayload } from "../auth/authStorage";

/**
 * Miroir de `ActivityResponse` côté backend (LS-002) : les mêmes
 * champs que `/nearby`/`/within-bounds` (voir l'interface `Activity`
 * dans `App.tsx`), pour pouvoir réutiliser le même modal de détail que
 * la carte principale (critère d'acceptation « un événement liké peut
 * être ouvert depuis la liste »).
 */
interface FavoriteActivity {
    id: number;
    title: string;
    category: string;
    startDate: string;
    address: string | null;
    city: string | null;
    sourceName: string;
    likedByCurrentUser: boolean;
    /** LL-11015 : voir `Activity.mainImageUrl` dans `App.tsx`, même origine (`ActivityResponse.mainImageUrl`). */
    mainImageUrl: string | null;
}

interface ApiErrorBody {
    message: string;
}

function formatLocation(activity: FavoriteActivity): string {
    if (activity.address && activity.city) {
        return `${activity.address}, ${activity.city}`;
    }
    return activity.city ?? activity.address ?? "Lieu non renseigné";
}

/**
 * Page "Mes favoris" (LS-002, critère d'acceptation « les événements
 * likés sont accessibles depuis l'interface utilisateur » / « la liste
 * [...] est persistante »). Même patron que ProfilePage.tsx : garde
 * d'accès UX (protection réelle côté backend, voir SecurityConfig),
 * chargement via apiFetch.
 */
function FavoritesPage() {
    const navigate = useNavigate();
    const [favorites, setFavorites] = useState<FavoriteActivity[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    // Id de l'activité en cours de retrait/like, pour désactiver son seul bouton
    // pendant l'appel (voir handleToggleLike) plutôt qu'un état de chargement global.
    const [pendingId, setPendingId] = useState<number | null>(null);
    /**
     * Activité actuellement ouverte dans le mini-détail (critère
     * d'acceptation « un clic sur un événement permet d'accéder à son
     * détail » / « un événement liké peut être ouvert depuis la
     * liste »).
     */
    const [selectedFavorite, setSelectedFavorite] = useState<FavoriteActivity | null>(null);

    useEffect(() => {
        if (!getPayload()) {
            navigate("/login");
        }
    }, [navigate]);

    useEffect(() => {
        const abortController = new AbortController();
        setIsLoading(true);
        setLoadError(null);

        async function loadFavorites() {
            try {
                const response = await apiFetch("/api/v1/users/me/likes", { signal: abortController.signal });
                if (response.ok) {
                    const data: FavoriteActivity[] = await response.json();
                    setFavorites(data);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setLoadError(body?.message ?? "Impossible de charger tes favoris, réessaie.");
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setLoadError("Impossible de contacter le serveur, réessaie plus tard.");
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoading(false);
                }
            }
        }

        void loadFavorites();

        return () => abortController.abort();
    }, []);

    /**
     * Retire une activité des favoris (toujours un retrait ici : cette page
     * n'affiche que des activités déjà likées, il n'y a pas de sens à liker
     * depuis cette liste). Retrait local de la liste plutôt qu'un refetch
     * complet : le seul changement possible suite à cette action est la
     * disparition de cette unique activité (critère d'acceptation « un
     * événement retiré des favoris disparaît de cette liste »).
     */
    async function handleUnlike(activityId: number) {
        setPendingId(activityId);
        try {
            const response = await apiFetch(`/api/v1/activities/${activityId}/like`, { method: "DELETE" });
            if (response.ok) {
                setFavorites((current) => current.filter((activity) => activity.id !== activityId));
                setSelectedFavorite((current) => (current?.id === activityId ? null : current));
            }
        } catch {
            // Échec silencieux (le favori reste affiché, l'utilisateur peut réessayer) :
            // suffisant pour une action secondaire, pas la peine d'un état d'erreur
            // pour toute la page.
        } finally {
            setPendingId(null);
        }
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Mes favoris</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            <main className="favorites-content">
                {isLoading && <p className="activities-status">Chargement de tes favoris…</p>}
                {!isLoading && loadError && (
                    <p className="activities-status activities-status-error">{loadError}</p>
                )}
                {!isLoading && !loadError && favorites.length === 0 && (
                    <p className="activities-status">
                        Tu n'as encore liké aucune activité. Clique sur le cœur d'une activité pour l'ajouter ici.
                    </p>
                )}
                {!isLoading && !loadError && favorites.length > 0 && (
                    <ul className="favorites-list">
                        {favorites.map((activity) => (
                            <li className="favorites-list-item" key={activity.id}>
                                <button
                                    className="favorites-list-item-info"
                                    onClick={() => setSelectedFavorite(activity)}
                                    type="button"
                                >
                                    {/* LL-11015, critère d'acceptation « images visibles ». */}
                                    {activity.mainImageUrl && (
                                        <img
                                            alt=""
                                            className="favorites-list-item-thumbnail"
                                            loading="lazy"
                                            src={activity.mainImageUrl}
                                        />
                                    )}
                                    <span className="favorites-list-item-title">{activity.title}</span>
                                    <span className="favorites-list-item-meta">
                                        {new Date(activity.startDate).toLocaleDateString("fr-FR", {
                                            dateStyle: "long",
                                        })}
                                        {" · "}
                                        {formatLocation(activity)}
                                        {activity.category ? ` · ${activity.category}` : ""}
                                    </span>
                                </button>
                                <button
                                    aria-label={`Retirer ${activity.title} des favoris`}
                                    className="favorites-list-item-remove"
                                    disabled={pendingId === activity.id}
                                    onClick={() => void handleUnlike(activity.id)}
                                    type="button"
                                >
                                    ♥
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </main>

            {/*
              Mini-détail (critère d'acceptation "accéder à son détail" depuis la
              liste) : mêmes classes CSS que le détail d'activité de la carte
              principale (App.tsx), pour un rendu cohérent sans dupliquer le style.
            */}
            {selectedFavorite && (
                <div
                    className="modal-overlay"
                    onClick={() => setSelectedFavorite(null)}
                    role="presentation"
                >
                    <div
                        aria-labelledby="favorite-detail-title"
                        aria-modal="true"
                        className="modal-dialog"
                        onClick={(event) => event.stopPropagation()}
                        role="dialog"
                    >
                        <div className="modal-header">
                            <h2 id="favorite-detail-title">{selectedFavorite.title}</h2>
                            <button
                                aria-label="Fermer"
                                className="modal-close-button"
                                onClick={() => setSelectedFavorite(null)}
                                type="button"
                            >
                                ✕
                            </button>
                        </div>
                        <div className="activity-detail-body">
                            <p className="activity-detail-row">
                                <strong>Lieu :</strong> {formatLocation(selectedFavorite)}
                            </p>
                            <p className="activity-detail-row">
                                <strong>Date :</strong>{" "}
                                {new Date(selectedFavorite.startDate).toLocaleString("fr-FR", {
                                    dateStyle: "long",
                                    timeStyle: "short",
                                })}
                            </p>
                            {selectedFavorite.category && (
                                <p className="activity-detail-row">
                                    <strong>Catégorie :</strong> {selectedFavorite.category}
                                </p>
                            )}
                            <p className="activity-detail-row">
                                <strong>Source :</strong> {selectedFavorite.sourceName}
                            </p>
                            <div className="activity-detail-actions">
                                <button
                                    className="cancel-activity-button"
                                    disabled={pendingId === selectedFavorite.id}
                                    onClick={() => void handleUnlike(selectedFavorite.id)}
                                    type="button"
                                >
                                    ♥ Retirer des favoris
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default FavoritesPage;
