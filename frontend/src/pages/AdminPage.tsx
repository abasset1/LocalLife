import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/apiClient";
import { getPayload } from "../auth/authStorage";

/**
 * LL-EF-004 : représentation d'une activité pour l'interface
 * d'administration. Distincte du type `Activity` utilisé côté carte
 * (`App.tsx`, qui consomme `ActivityResponse` — voir sa javadoc côté
 * backend) : `GET /api/v1/admin/activities` renvoie l'entité de domaine
 * `Activity` telle quelle (LL-6005), avec `sourceId`/`importKey` plutôt
 * que `sourceName`, et `description`/`endDate`/`url` que la vue carte
 * n'exposait pas jusqu'ici.
 */
interface AdminActivity {
    id: number;
    title: string;
    description: string;
    category: string;
    latitude: number;
    longitude: number;
    startDate: string;
    endDate: string;
    status: string;
    sourceId: number;
    importKey: string | null;
    url: string | null;
}

interface ApiErrorBody {
    message: string;
}

/**
 * Statuts de modération formalisés en LL-6003 (voir la javadoc de
 * `Activity` côté backend) — trois valeurs, aucune autre n'existe.
 * Le point d'architecture demandé par LL-EF-004 (« prévoir un véritable
 * statut de modération PENDING/APPROVED/REJECTED ») était déjà couvert
 * par ce travail du Sprint 6 (`PUBLISHED` au lieu d'`APPROVED`,
 * fonctionnellement équivalent) : ce ticket ne fait qu'ajouter
 * l'interface qui pilote ces transitions, déjà exposées par
 * `AdminActivityController`/protégées par `SecurityConfig` (rôle
 * `ADMIN`, `PATCH .../publish` et `.../reject`) depuis LL-6006.
 */
const STATUS_TABS: { value: string; label: string }[] = [
    { value: "PENDING", label: "En attente" },
    { value: "PUBLISHED", label: "Publiées" },
    { value: "REJECTED", label: "Rejetées" },
];

function formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("fr-FR");
}

function AdminPage() {
    const navigate = useNavigate();
    const [selectedStatus, setSelectedStatus] = useState(STATUS_TABS[0].value);
    const [activities, setActivities] = useState<AdminActivity[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    // Id de l'activité dont l'action (valider/refuser) est en cours, pour
    // désactiver ses boutons le temps de la requête et éviter un double clic.
    const [pendingActionId, setPendingActionId] = useState<number | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    /**
     * Garde d'accès (critère d'acceptation « un utilisateur non
     * administrateur ne peut pas accéder à l'interface ») : redirection
     * immédiate si le JWT stocké est absent ou ne porte pas le rôle
     * `ADMIN`. Purement une question d'UX — la protection réelle est
     * assurée côté backend par `SecurityConfig` (voir plus haut), qui
     * refuse déjà `GET`/`PATCH /api/v1/admin/activities/...` à quiconque
     * n'a pas le rôle `ADMIN`, JWT falsifié ou navigation directe
     * inclus.
     */
    useEffect(() => {
        const payload = getPayload();
        if (!payload) {
            navigate("/login");
            return;
        }
        if (payload.role !== "ADMIN") {
            navigate("/");
        }
    }, [navigate]);

    useEffect(() => {
        const abortController = new AbortController();
        setIsLoading(true);
        setLoadError(null);
        setActionError(null);

        async function loadActivities() {
            try {
                const response = await apiFetch(
                    `/api/v1/admin/activities?status=${selectedStatus}`,
                    { signal: abortController.signal },
                );

                if (response.ok) {
                    const data: AdminActivity[] = await response.json();
                    setActivities(data);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setLoadError(body?.message ?? "Impossible de charger les activités, réessaie.");
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

        void loadActivities();

        return () => abortController.abort();
    }, [selectedStatus]);

    /**
     * Valide (`publish`) ou rejette (`reject`) une activité `PENDING`.
     * En cas de succès, retire simplement l'activité de la liste
     * affichée plutôt que de la mettre à jour sur place : son nouveau
     * statut ne correspond plus à l'onglet actuellement sélectionné
     * (LL-6006 : transitions `PENDING → PUBLISHED`/`REJECTED` uniquement,
     * jamais de retour en arrière), elle n'a donc plus sa place ici.
     */
    async function handleModerationAction(id: number, action: "publish" | "reject") {
        setPendingActionId(id);
        setActionError(null);

        try {
            const response = await apiFetch(`/api/v1/admin/activities/${id}/${action}`, {
                method: "PATCH",
            });

            if (response.ok) {
                setActivities((current) => current.filter((activity) => activity.id !== id));
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setActionError(body?.message ?? "Action impossible, réessaie.");
            }
        } catch {
            setActionError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setPendingActionId(null);
        }
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Administration — Modération des activités</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            <nav className="admin-tabs">
                {STATUS_TABS.map((tab) => (
                    <button
                        className={tab.value === selectedStatus ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={tab.value}
                        onClick={() => setSelectedStatus(tab.value)}
                        type="button"
                    >
                        {tab.label}
                    </button>
                ))}
            </nav>

            {actionError && <p className="activities-status activities-status-error">{actionError}</p>}

            <main className="admin-content">
                {isLoading && <p className="activities-status">Chargement des activités…</p>}
                {!isLoading && loadError && (
                    <p className="activities-status activities-status-error">{loadError}</p>
                )}
                {!isLoading && !loadError && activities.length === 0 && (
                    <p className="activities-status">Aucune activité dans cette catégorie.</p>
                )}
                {!isLoading && !loadError && activities.length > 0 && (
                    <ul className="admin-activity-list">
                        {activities.map((activity) => (
                            <li className="admin-activity-card" key={activity.id}>
                                <div className="admin-activity-header">
                                    <h2>{activity.title}</h2>
                                    <span className={`admin-status-badge admin-status-${activity.status.toLowerCase()}`}>
                                        {activity.status}
                                    </span>
                                </div>
                                <p className="admin-activity-description">{activity.description}</p>
                                <dl className="admin-activity-meta">
                                    <div>
                                        <dt>Catégorie</dt>
                                        <dd>{activity.category}</dd>
                                    </div>
                                    <div>
                                        <dt>Début</dt>
                                        <dd>{formatDate(activity.startDate)}</dd>
                                    </div>
                                    <div>
                                        <dt>Fin</dt>
                                        <dd>{formatDate(activity.endDate)}</dd>
                                    </div>
                                    <div>
                                        <dt>Position</dt>
                                        <dd>{activity.latitude.toFixed(4)}, {activity.longitude.toFixed(4)}</dd>
                                    </div>
                                </dl>
                                {selectedStatus === "PENDING" && (
                                    <div className="admin-activity-actions">
                                        <button
                                            className="admin-approve-button"
                                            disabled={pendingActionId === activity.id}
                                            onClick={() => void handleModerationAction(activity.id, "publish")}
                                            type="button"
                                        >
                                            Valider
                                        </button>
                                        <button
                                            className="admin-reject-button"
                                            disabled={pendingActionId === activity.id}
                                            onClick={() => void handleModerationAction(activity.id, "reject")}
                                            type="button"
                                        >
                                            Refuser
                                        </button>
                                    </div>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </main>
        </div>
    );
}

export default AdminPage;
