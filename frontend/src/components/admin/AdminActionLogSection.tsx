import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { proRequest } from "../pro/proApi";

/** Entrée du journal des actions d'administration (`GET /api/v1/admin/action-log`). */
interface ActionLogEntry {
    id: number;
    occurredAt: string;
    actorId: number | null;
    actorEmail: string | null;
    action: string;
    targetType: string | null;
    targetId: number | string | null;
    summary: string | null;
    /** États avant/après, en JSON (chaîne), ou `null`. */
    before: string | null;
    after: string | null;
    reason: string | null;
}

interface ActionLogPage {
    items: ActionLogEntry[];
    total: number;
    page: number;
    size: number;
}

const PAGE_SIZE = 25;
const EMPTY_FILTERS = { action: "", targetType: "", targetId: "" };

/** JSON compact et lisible : indenté s'il se lit, tel quel sinon. */
function formatState(value: string): string {
    try {
        return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
        return value;
    }
}

/**
 * Journal des actions d'administration : qui a fait quoi, sur quoi, quand, avec quel motif, et l'état avant/après.
 * Filtres par action, type et identifiant de cible ; pagination côté serveur.
 */
function AdminActionLogSection() {
    const [form, setForm] = useState(EMPTY_FILTERS);
    const [filters, setFilters] = useState(EMPTY_FILTERS);
    const [page, setPage] = useState(0);
    const [data, setData] = useState<ActionLogPage | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        (Object.keys(filters) as (keyof typeof filters)[]).forEach((key) => {
            if (filters[key].trim()) {
                params.set(key, filters[key].trim());
            }
        });
        void proRequest<ActionLogPage>(`/api/v1/admin/action-log?${params}`).then((result) => {
            if (result.ok) {
                setData(result.data);
                setError(null);
            } else {
                setError(result.message);
            }
        });
    }, [filters, page]);

    function applyFilters(event: FormEvent) {
        event.preventDefault();
        setPage(0);
        setFilters(form);
    }

    const pageCount = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

    return (
        <section aria-labelledby="admin-action-log-title">
            <h2 id="admin-action-log-title">Journal</h2>
            <p className="activities-status">Actions des administrateurs, les plus récentes d'abord.</p>
            <form className="admin-deletion-form" onSubmit={applyFilters}>
                <label>
                    Action
                    <input onChange={(event) => setForm({ ...form, action: event.target.value })}
                        placeholder="ACTIVITY_REJECT…" type="text" value={form.action} />
                </label>
                <label>
                    Type de cible
                    <input onChange={(event) => setForm({ ...form, targetType: event.target.value })}
                        placeholder="ACTIVITY, USER…" type="text" value={form.targetType} />
                </label>
                <label>
                    Identifiant de cible
                    <input onChange={(event) => setForm({ ...form, targetId: event.target.value })} type="text"
                        value={form.targetId} />
                </label>
                <div className="admin-activity-actions">
                    <button className="admin-import-button" type="submit">Filtrer</button>
                    <button className="contribution-action" type="button" onClick={() => {
                        setForm(EMPTY_FILTERS);
                        setFilters(EMPTY_FILTERS);
                        setPage(0);
                    }}>
                        Effacer
                    </button>
                </div>
            </form>

            {error && <p className="activities-status activities-status-error" role="alert">{error}</p>}
            {data && data.items.length === 0 && <p className="activities-status">Aucune action enregistrée.</p>}
            {data && data.items.length > 0 && (
                <>
                    <p className="activities-status">{data.total} action{data.total > 1 ? "s" : ""}.</p>
                    <ul className="favorites-list">
                        {data.items.map((entry) => (
                            <li className="favorites-list-item admin-log-entry" key={entry.id}>
                                <div className="favorites-list-item-info">
                                    <span className="favorites-list-item-title">
                                        {entry.action}
                                        {entry.targetType ? ` · ${entry.targetType}` : ""}
                                        {entry.targetId !== null ? ` n° ${entry.targetId}` : ""}
                                    </span>
                                    <span className="favorites-list-item-meta">
                                        {new Date(entry.occurredAt).toLocaleString("fr-FR")}
                                        {` · ${entry.actorEmail ?? (entry.actorId !== null ? `compte n° ${entry.actorId}` : "système")}`}
                                    </span>
                                    {entry.summary && <span className="favorites-list-item-meta">{entry.summary}</span>}
                                    {entry.reason && <span className="favorites-list-item-meta">Motif : {entry.reason}</span>}
                                    {(entry.before || entry.after) && (
                                        <details>
                                            <summary>Avant / après</summary>
                                            <div className="admin-log-states">
                                                <div>
                                                    <strong>Avant</strong>
                                                    <pre>{entry.before ? formatState(entry.before) : "—"}</pre>
                                                </div>
                                                <div>
                                                    <strong>Après</strong>
                                                    <pre>{entry.after ? formatState(entry.after) : "—"}</pre>
                                                </div>
                                            </div>
                                        </details>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>
                    {pageCount > 1 && (
                        <div className="admin-activity-actions">
                            <button className="contribution-action" disabled={page === 0}
                                onClick={() => setPage((current) => current - 1)} type="button">
                                ← Précédente
                            </button>
                            <span>Page {page + 1} / {pageCount}</span>
                            <button className="contribution-action" disabled={page + 1 >= pageCount}
                                onClick={() => setPage((current) => current + 1)} type="button">
                                Suivante →
                            </button>
                        </div>
                    )}
                </>
            )}
        </section>
    );
}

export default AdminActionLogSection;
