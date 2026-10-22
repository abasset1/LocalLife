import { useEffect, useState } from "react";
import { proRequest } from "../pro/proApi";

/** Activité à la corbeille (`GET /api/v1/admin/activities/trash`). */
interface TrashedActivity {
    id: number;
    title: string;
    city: string | null;
    startDate: string | null;
    previousStatus: string;
    sourceId: number | null;
    createdBy: number | null;
    deletedAt: string;
    deletedBy: number | null;
    deletedByEmail: string | null;
    deletionReason: string | null;
    deletionBatch: string | null;
}

interface TrashPage {
    items: TrashedActivity[];
    total: number;
    page: number;
    size: number;
}

const PAGE_SIZE = 20;

const STATUS_LABELS: Record<string, string> = {
    PENDING: "En attente",
    PUBLISHED: "Publiée",
    REJECTED: "Refusée",
    CANCELLED: "Annulée",
    ARCHIVED: "Archivée",
};

function formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString("fr-FR", { dateStyle: "medium", timeStyle: "short" }) : "";
}

/**
 * Corbeille des activités : les activités supprimées (une à une ou par lot) y restent 30 jours avant d'être
 * purgées. Une activité se restaure dans son statut précédent ; un lot supprimé ensemble se restaure d'un coup.
 */
function AdminTrashSection({ onRestored }: { onRestored?: () => void }) {
    const [page, setPage] = useState(0);
    const [data, setData] = useState<TrashPage | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [message, setMessage] = useState<{ kind: "success" | "error"; text: string } | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<TrashPage>(`/api/v1/admin/activities/trash?page=${page}&size=${PAGE_SIZE}`).then((result) => {
            if (result.ok) {
                setData(result.data);
            } else {
                setMessage({ kind: "error", text: result.message });
            }
        });
    }, [page, refreshKey]);

    async function restore(item: TrashedActivity) {
        setBusy(true);
        const result = await proRequest(`/api/v1/admin/activities/${item.id}/restore`, "POST");
        setBusy(false);
        setMessage(result.ok
            ? { kind: "success", text: `« ${item.title} » restaurée.` }
            : { kind: "error", text: result.status === 404 ? "Cette activité n'est plus dans la corbeille." : result.message });
        setRefreshKey((key) => key + 1);
        if (result.ok) {
            onRestored?.();
        }
    }

    async function restoreBatch(batch: string) {
        setBusy(true);
        const result = await proRequest<{ count: number }>(
            `/api/v1/admin/activities/trash/batches/${encodeURIComponent(batch)}/restore`, "POST");
        setBusy(false);
        setMessage(result.ok
            ? { kind: "success", text: `Lot ${batch} : ${result.data.count} activité${result.data.count > 1 ? "s" : ""} restaurée${result.data.count > 1 ? "s" : ""}.` }
            : { kind: "error", text: result.message });
        setRefreshKey((key) => key + 1);
        if (result.ok) {
            onRestored?.();
        }
    }

    const pageCount = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

    return (
        <section aria-labelledby="admin-trash-title">
            <h2 id="admin-trash-title">Corbeille</h2>
            <p className="activities-status">
                Activités supprimées, restaurables dans leur statut précédent. Elles sont purgées définitivement après
                30 jours.
            </p>
            {message && (
                <p className={message.kind === "error" ? "activities-status activities-status-error" : "activities-status"}
                    role={message.kind === "error" ? "alert" : "status"}>
                    {message.text}
                </p>
            )}
            {data && data.items.length === 0 && <p className="activities-status">La corbeille est vide.</p>}
            {data && data.items.length > 0 && (
                <>
                    <p className="activities-status">{data.total} activité{data.total > 1 ? "s" : ""} à la corbeille.</p>
                    <ul className="favorites-list">
                        {data.items.map((item) => (
                            <li className="favorites-list-item contributions-list-item" key={item.id}>
                                <div className="favorites-list-item-info">
                                    <span className="favorites-list-item-title">{item.title}</span>
                                    <span className="favorites-list-item-meta">
                                        {STATUS_LABELS[item.previousStatus] ?? item.previousStatus}
                                        {item.city ? ` · ${item.city}` : ""}
                                        {` · supprimée le ${formatDateTime(item.deletedAt)}`}
                                        {item.deletedByEmail ? ` par ${item.deletedByEmail}` : ""}
                                    </span>
                                    {item.deletionReason && (
                                        <span className="favorites-list-item-meta">Motif : {item.deletionReason}</span>
                                    )}
                                    {item.deletionBatch && (
                                        <span className="favorites-list-item-meta">Lot {item.deletionBatch}</span>
                                    )}
                                </div>
                                <div className="contributions-list-item-actions">
                                    <button className="contribution-action" disabled={busy}
                                        onClick={() => void restore(item)} type="button">
                                        Restaurer
                                    </button>
                                    {item.deletionBatch && (
                                        <button className="contribution-action" disabled={busy}
                                            onClick={() => void restoreBatch(item.deletionBatch as string)} type="button">
                                            Restaurer le lot
                                        </button>
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

export default AdminTrashSection;
