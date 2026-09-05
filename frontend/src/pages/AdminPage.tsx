import { useEffect, useState } from "react";
import type { FormEvent } from "react";
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
 * LL-EF-005 : représentation d'une source/agenda pour l'interface
 * d'administration — miroir exact de l'entité de domaine `Source` côté
 * backend (`GET /api/v1/sources` la renvoie telle quelle, comme
 * `AdminActivity` pour `Activity`). `agendaUid`/`regionFilter` ne sont
 * significatifs que pour une source de type `API` destinée à la collecte
 * OpenAgenda (voir `OpenAgendaCollectorFactory` côté backend) —
 * `null` pour `RSS`/`MANUAL`.
 */
interface Source {
    id: number;
    name: string;
    type: string;
    url: string | null;
    status: string;
    lastSyncAt: string | null;
    agendaUid: string | null;
    regionFilter: string | null;
}

/**
 * Décision Alex (LL-EF-005) : CRUD générique sur tous les types de source
 * existants, pas seulement OpenAgenda — voir `SOURCE_CONTRACT.md`.
 */
const SOURCE_TYPES = ["API", "RSS", "MANUAL"];
const SOURCE_STATUSES = ["ACTIVE", "INACTIVE", "ERROR"];

/** Sections principales de l'interface d'administration (LL-EF-004 + LL-EF-005). */
const ADMIN_SECTIONS = [
    { value: "moderation", label: "Modération des activités" },
    { value: "agendas", label: "Agendas" },
] as const;

type AdminSection = (typeof ADMIN_SECTIONS)[number]["value"];

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
    const [activeSection, setActiveSection] = useState<AdminSection>(ADMIN_SECTIONS[0].value);
    const [selectedStatus, setSelectedStatus] = useState(STATUS_TABS[0].value);
    const [activities, setActivities] = useState<AdminActivity[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    // Id de l'activité dont l'action (valider/refuser) est en cours, pour
    // désactiver ses boutons le temps de la requête et éviter un double clic.
    const [pendingActionId, setPendingActionId] = useState<number | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    // --- LL-EF-005 : état de la section Agendas ---
    const [sources, setSources] = useState<Source[]>([]);
    const [isLoadingSources, setIsLoadingSources] = useState(true);
    const [sourcesError, setSourcesError] = useState<string | null>(null);
    const [deletingSourceId, setDeletingSourceId] = useState<number | null>(null);
    // Formulaire de création/édition : `isSourceFormOpen` pilote sa visibilité,
    // `editingSource` distingue édition (non `null`, pré-remplit les champs) de
    // création (`null`) — voir `openCreateSourceForm`/`openEditSourceForm` ci-dessous.
    const [isSourceFormOpen, setIsSourceFormOpen] = useState(false);
    const [editingSource, setEditingSource] = useState<Source | null>(null);
    const [sourceFormError, setSourceFormError] = useState<string | null>(null);
    const [isSavingSource, setIsSavingSource] = useState(false);
    const [sourceName, setSourceName] = useState("");
    const [sourceType, setSourceType] = useState<string>(SOURCE_TYPES[0]);
    const [sourceUrl, setSourceUrl] = useState("");
    const [sourceStatus, setSourceStatus] = useState<string>(SOURCE_STATUSES[0]);
    const [sourceAgendaUid, setSourceAgendaUid] = useState("");
    const [sourceRegionFilter, setSourceRegionFilter] = useState("");
    // Incrémenté après chaque création/modification/suppression réussie pour redéclencher
    // le chargement de la liste (même schéma que `refreshKey` dans App.tsx pour les activités).
    const [sourcesRefreshKey, setSourcesRefreshKey] = useState(0);

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

    // --- LL-EF-005 : chargement de la liste des agendas/sources ---
    useEffect(() => {
        if (activeSection !== "agendas") {
            return;
        }

        const abortController = new AbortController();
        setIsLoadingSources(true);
        setSourcesError(null);

        async function loadSources() {
            try {
                const response = await apiFetch("/api/v1/sources", { signal: abortController.signal });
                if (response.ok) {
                    const data: Source[] = await response.json();
                    setSources(data);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setSourcesError(body?.message ?? "Impossible de charger les agendas, réessaie.");
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setSourcesError("Impossible de contacter le serveur, réessaie plus tard.");
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoadingSources(false);
                }
            }
        }

        void loadSources();

        return () => abortController.abort();
    }, [activeSection, sourcesRefreshKey]);

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

    function openCreateSourceForm() {
        setEditingSource(null);
        setSourceName("");
        setSourceType(SOURCE_TYPES[0]);
        setSourceUrl("");
        setSourceStatus(SOURCE_STATUSES[0]);
        setSourceAgendaUid("");
        setSourceRegionFilter("");
        setSourceFormError(null);
        setIsSourceFormOpen(true);
    }

    function openEditSourceForm(source: Source) {
        setEditingSource(source);
        setSourceName(source.name);
        setSourceType(source.type);
        setSourceUrl(source.url ?? "");
        setSourceStatus(source.status);
        setSourceAgendaUid(source.agendaUid ?? "");
        setSourceRegionFilter(source.regionFilter ?? "");
        setSourceFormError(null);
        setIsSourceFormOpen(true);
    }

    function closeSourceForm() {
        setIsSourceFormOpen(false);
        setSourceFormError(null);
    }

    /**
     * Crée ou modifie un agenda/source selon que {@link editingSource}
     * est renseigné. Le statut n'est éditable qu'en modification : à la
     * création, le backend impose toujours `ACTIVE`
     * (`SourceService#createSource`), cohérent avec le formulaire qui ne
     * propose ce champ qu'en mode édition (voir le rendu plus bas).
     */
    async function handleSubmitSourceForm(event: FormEvent) {
        event.preventDefault();
        setIsSavingSource(true);
        setSourceFormError(null);

        const agendaUid = sourceAgendaUid.trim() === "" ? null : sourceAgendaUid.trim();
        const regionFilter = sourceRegionFilter.trim() === "" ? null : sourceRegionFilter.trim();
        const url = sourceUrl.trim() === "" ? null : sourceUrl.trim();

        try {
            const response = editingSource
                ? await apiFetch(`/api/v1/sources/${editingSource.id}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        name: sourceName,
                        type: sourceType,
                        url,
                        status: sourceStatus,
                        agendaUid,
                        regionFilter,
                    }),
                })
                : await apiFetch("/api/v1/sources", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ name: sourceName, type: sourceType, url, agendaUid, regionFilter }),
                });

            if (response.ok) {
                setIsSourceFormOpen(false);
                setSourcesRefreshKey((current) => current + 1);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSourceFormError(body?.message ?? "Impossible d'enregistrer cet agenda, réessaie.");
            }
        } catch {
            setSourceFormError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSavingSource(false);
        }
    }

    /**
     * Supprime un agenda/source, après confirmation de l'utilisateur
     * (critère d'acceptation « demander une confirmation avant la
     * suppression »). Les activités qui lui étaient rattachées sont
     * détachées vers la source réservée « Saisie manuelle » par le
     * backend (`SourceService#deleteSource`, décision Alex) : rien à
     * faire de plus ici une fois la requête acceptée.
     */
    async function handleDeleteSource(source: Source) {
        const confirmed = window.confirm(
            `Supprimer l'agenda « ${source.name} » ? Les activités qui lui sont rattachées seront `
                + "conservées, mais réattribuées à la source « Saisie manuelle ».",
        );
        if (!confirmed) {
            return;
        }

        setDeletingSourceId(source.id);
        setSourcesError(null);

        try {
            const response = await apiFetch(`/api/v1/sources/${source.id}`, { method: "DELETE" });
            if (response.ok) {
                setSourcesRefreshKey((current) => current + 1);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSourcesError(body?.message ?? "Impossible de supprimer cet agenda, réessaie.");
            }
        } catch {
            setSourcesError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setDeletingSourceId(null);
        }
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Administration</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            <nav className="admin-tabs admin-section-tabs">
                {ADMIN_SECTIONS.map((section) => (
                    <button
                        className={section.value === activeSection ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={section.value}
                        onClick={() => setActiveSection(section.value)}
                        type="button"
                    >
                        {section.label}
                    </button>
                ))}
            </nav>

            {activeSection === "moderation" ? (
                <>
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
                                            <span
                                                className={`admin-status-badge admin-status-${activity.status.toLowerCase()}`}
                                            >
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
                                                <dd>
                                                    {activity.latitude.toFixed(4)}, {activity.longitude.toFixed(4)}
                                                </dd>
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
                </>
            ) : (
                <main className="admin-content">
                    <div className="admin-agendas-toolbar">
                        <button className="header-add-activity-button" onClick={openCreateSourceForm} type="button">
                            Ajouter un agenda
                        </button>
                    </div>

                    {sourcesError && <p className="activities-status activities-status-error">{sourcesError}</p>}

                    {isSourceFormOpen && (
                        <div className="modal-overlay" onClick={closeSourceForm} role="presentation">
                            <div
                                aria-labelledby="source-form-title"
                                aria-modal="true"
                                className="modal-dialog"
                                onClick={(event) => event.stopPropagation()}
                                role="dialog"
                            >
                                <div className="modal-header">
                                    <h2 id="source-form-title">
                                        {editingSource ? "Modifier l'agenda" : "Ajouter un agenda"}
                                    </h2>
                                    <button
                                        aria-label="Fermer"
                                        className="modal-close-button"
                                        onClick={closeSourceForm}
                                        type="button"
                                    >
                                        ✕
                                    </button>
                                </div>
                                <form className="contribution-form" onSubmit={(event) => void handleSubmitSourceForm(event)}>
                                    <div className="form-field">
                                        <label htmlFor="source-name">Nom</label>
                                        <input
                                            id="source-name"
                                            onChange={(event) => setSourceName(event.target.value)}
                                            required
                                            type="text"
                                            value={sourceName}
                                        />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="source-type">Type</label>
                                        <select
                                            id="source-type"
                                            onChange={(event) => setSourceType(event.target.value)}
                                            value={sourceType}
                                        >
                                            {SOURCE_TYPES.map((type) => (
                                                <option key={type} value={type}>
                                                    {type}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                    {editingSource && (
                                        <div className="form-field">
                                            <label htmlFor="source-status">Statut</label>
                                            <select
                                                id="source-status"
                                                onChange={(event) => setSourceStatus(event.target.value)}
                                                value={sourceStatus}
                                            >
                                                {SOURCE_STATUSES.map((status) => (
                                                    <option key={status} value={status}>
                                                        {status}
                                                    </option>
                                                ))}
                                            </select>
                                        </div>
                                    )}
                                    <div className="form-field">
                                        <label htmlFor="source-url">URL</label>
                                        <input
                                            id="source-url"
                                            onChange={(event) => setSourceUrl(event.target.value)}
                                            type="text"
                                            value={sourceUrl}
                                        />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="source-agenda-uid">
                                            Identifiant d'agenda OpenAgenda
                                        </label>
                                        <input
                                            id="source-agenda-uid"
                                            onChange={(event) => setSourceAgendaUid(event.target.value)}
                                            placeholder="Uniquement pour un agenda de type API destiné à OpenAgenda"
                                            type="text"
                                            value={sourceAgendaUid}
                                        />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="source-region-filter">Filtre région (optionnel)</label>
                                        <input
                                            id="source-region-filter"
                                            onChange={(event) => setSourceRegionFilter(event.target.value)}
                                            type="text"
                                            value={sourceRegionFilter}
                                        />
                                    </div>
                                    <div className="modal-actions">
                                        <button disabled={isSavingSource} type="submit">
                                            {editingSource ? "Enregistrer" : "Ajouter"}
                                        </button>
                                    </div>
                                    {sourceFormError && (
                                        <span className="form-message form-message-error">{sourceFormError}</span>
                                    )}
                                </form>
                            </div>
                        </div>
                    )}

                    {isLoadingSources && <p className="activities-status">Chargement des agendas…</p>}
                    {!isLoadingSources && sources.length === 0 && (
                        <p className="activities-status">Aucun agenda configuré.</p>
                    )}
                    {!isLoadingSources && sources.length > 0 && (
                        <ul className="admin-activity-list">
                            {sources.map((source) => (
                                <li className="admin-activity-card" key={source.id}>
                                    <div className="admin-activity-header">
                                        <h2>{source.name}</h2>
                                        <span
                                            className={`admin-status-badge admin-status-${source.status.toLowerCase()}`}
                                        >
                                            {source.status}
                                        </span>
                                    </div>
                                    <dl className="admin-activity-meta">
                                        <div>
                                            <dt>Type</dt>
                                            <dd>{source.type}</dd>
                                        </div>
                                        <div>
                                            <dt>Identifiant d'agenda</dt>
                                            <dd>{source.agendaUid ?? "—"}</dd>
                                        </div>
                                        <div>
                                            <dt>Filtre région</dt>
                                            <dd>{source.regionFilter ?? "—"}</dd>
                                        </div>
                                        <div>
                                            <dt>Dernière synchronisation</dt>
                                            <dd>{source.lastSyncAt ? formatDate(source.lastSyncAt) : "—"}</dd>
                                        </div>
                                    </dl>
                                    <div className="admin-activity-actions">
                                        <button
                                            className="admin-approve-button"
                                            onClick={() => openEditSourceForm(source)}
                                            type="button"
                                        >
                                            Modifier
                                        </button>
                                        <button
                                            className="admin-reject-button"
                                            disabled={deletingSourceId === source.id}
                                            onClick={() => void handleDeleteSource(source)}
                                            type="button"
                                        >
                                            Supprimer
                                        </button>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </main>
            )}
        </div>
    );
}

export default AdminPage;
