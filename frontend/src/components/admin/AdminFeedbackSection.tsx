import { useEffect, useState } from "react";
import { proRequest } from "../pro/proApi";

/** Suggestion ou anomalie reçue (`GET /api/v1/admin/feedback`). */
export interface FeedbackItem {
    id: number;
    kind: "SUGGESTION" | "BUG";
    message: string;
    pageUrl: string | null;
    userAgent: string | null;
    status: "NEW" | "DONE";
    createdAt: string;
    handledAt: string | null;
    userId: number;
    username: string;
    email: string;
}

interface FeedbackPage {
    items: FeedbackItem[];
    total: number;
    newCount: number;
}

const PAGE_SIZE = 25;
const FILTERS = [
    { value: "NEW", label: "À traiter" },
    { value: "DONE", label: "Traités" },
    { value: "", label: "Tous" },
] as const;

const KIND_LABELS: Record<FeedbackItem["kind"], string> = { SUGGESTION: "💡 Suggestion", BUG: "🐞 Anomalie" };

interface AdminFeedbackSectionProps {
    /** Nombre de messages à traiter, pour la pastille de l'onglet. */
    onNewCountChange?: (count: number) => void;
}

/**
 * Suggestions et anomalies envoyées par les utilisateurs (formulaire « Une idée, un problème ? »), les plus récentes
 * d'abord : auteur (adresse cliquable pour lui répondre), page d'envoi, message. Un message se marque traité, ou de
 * nouveau à traiter. Chaque nouveau message prévient aussi les administrateurs par e-mail.
 */
function AdminFeedbackSection({ onNewCountChange }: AdminFeedbackSectionProps) {
    const [status, setStatus] = useState<string>("NEW");
    const [page, setPage] = useState(0);
    const [data, setData] = useState<FeedbackPage | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busyId, setBusyId] = useState<number | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        if (status) {
            params.set("status", status);
        }
        let isCurrent = true;
        void proRequest<FeedbackPage>(`/api/v1/admin/feedback?${params}`).then((result) => {
            if (!isCurrent) {
                return;
            }
            if (result.ok) {
                setData(result.data);
                setError(null);
                onNewCountChange?.(result.data.newCount);
            } else {
                setError(result.message);
            }
        });
        return () => {
            isCurrent = false;
        };
    }, [status, page, refreshKey]);

    async function setDone(item: FeedbackItem, done: boolean) {
        setBusyId(item.id);
        const result = await proRequest<FeedbackItem>(`/api/v1/admin/feedback/${item.id}`, "PATCH", { done });
        setBusyId(null);
        if (result.ok) {
            setRefreshKey((key) => key + 1);
        } else {
            setError(result.message);
        }
    }

    const pageCount = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

    return (
        <section aria-labelledby="admin-feedback-title">
            <h2 id="admin-feedback-title">Retours des utilisateurs</h2>
            <p className="activities-status">
                Suggestions et anomalies envoyées depuis le site, les plus récentes d'abord. Tu reçois aussi un e-mail
                à chaque nouveau message.
            </p>
            <nav aria-label="Filtrer les retours" className="admin-tabs">
                {FILTERS.map((filter) => (
                    <button
                        aria-pressed={status === filter.value}
                        className={status === filter.value ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={filter.value}
                        onClick={() => {
                            setStatus(filter.value);
                            setPage(0);
                        }}
                        type="button"
                    >
                        {filter.label}
                        {filter.value === "NEW" && data && data.newCount > 0 ? ` (${data.newCount})` : ""}
                    </button>
                ))}
            </nav>

            {error && <p className="activities-status activities-status-error" role="alert">{error}</p>}
            {data && data.items.length === 0 && (
                <p className="activities-status">{status === "NEW" ? "Aucun retour à traiter." : "Aucun retour."}</p>
            )}
            {data && data.items.length > 0 && (
                <>
                    <ul className="favorites-list">
                        {data.items.map((item) => (
                            <li className="favorites-list-item admin-feedback-item" key={item.id}>
                                <div className="favorites-list-item-info">
                                    <span className="favorites-list-item-title">
                                        <span className={`admin-feedback-kind admin-feedback-kind-${item.kind.toLowerCase()}`}>
                                            {KIND_LABELS[item.kind]}
                                        </span>
                                        {item.status === "DONE" && <span className="admin-feedback-done">Traité</span>}
                                    </span>
                                    <p className="admin-feedback-message">{item.message}</p>
                                    <span className="favorites-list-item-meta">
                                        {new Date(item.createdAt).toLocaleString("fr-FR")}
                                        {" · "}
                                        {item.username} (<a href={`mailto:${item.email}`}>{item.email}</a>)
                                    </span>
                                    {item.pageUrl && (
                                        <span className="favorites-list-item-meta">Page : {item.pageUrl}</span>
                                    )}
                                    {item.userAgent && (
                                        <span className="favorites-list-item-meta admin-feedback-agent">{item.userAgent}</span>
                                    )}
                                </div>
                                <div className="admin-activity-actions">
                                    <button
                                        className={item.status === "NEW" ? "admin-import-button" : "contribution-action"}
                                        disabled={busyId === item.id}
                                        onClick={() => void setDone(item, item.status === "NEW")}
                                        type="button"
                                    >
                                        {item.status === "NEW" ? "Marquer traité" : "Remettre à traiter"}
                                    </button>
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

export default AdminFeedbackSection;
