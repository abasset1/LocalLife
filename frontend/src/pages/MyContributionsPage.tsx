import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/apiClient";
import { getPayload } from "../auth/authStorage";
import type { ApiErrorBody, Contribution } from "../components/explorer/types";
import { formatRecurrenceRule } from "../utils/recurrence";

const STATUS_LABELS: Record<string, string> = {
    PENDING: "En attente de validation",
    PUBLISHED: "Publiée",
    REJECTED: "Refusée",
    CANCELLED: "Annulée",
    ARCHIVED: "Archivée",
};

/** Statuts modifiables côté backend (`ContributionService`) : une activité refusée peut être corrigée. */
const EDITABLE_STATUSES = new Set(["PENDING", "PUBLISHED", "REJECTED"]);

function formatWhen(contribution: Contribution): string {
    if (contribution.recurrence) {
        const rule = formatRecurrenceRule(`FREQ=WEEKLY;BYDAY=${contribution.recurrence.daysOfWeek.join(",")}`);
        return `${rule} à ${contribution.recurrence.startTime.slice(0, 5).replace(":", "h")}`;
    }
    if (!contribution.startDate) {
        return "Date non renseignée";
    }
    return new Date(contribution.startDate).toLocaleDateString("fr-FR", { dateStyle: "long" });
}

/**
 * « Mes contributions » : toutes les activités proposées par l'utilisateur, quel que soit leur statut
 * (y compris en attente de validation ou refusées, invisibles sur la carte), pour les modifier ou les
 * supprimer. La modification ouvre le formulaire de l'Explorer (`/?edit=<id>`).
 */
function MyContributionsPage() {
    const navigate = useNavigate();
    const [contributions, setContributions] = useState<Contribution[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [pendingId, setPendingId] = useState<number | null>(null);

    useEffect(() => {
        if (!getPayload()) {
            navigate("/login");
        }
    }, [navigate]);

    useEffect(() => {
        const abortController = new AbortController();

        async function loadContributions() {
            try {
                const response = await apiFetch("/api/v1/activities/mine", { signal: abortController.signal });
                if (response.ok) {
                    setContributions((await response.json()) as Contribution[]);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setLoadError(body?.message ?? "Impossible de charger tes contributions, réessaie.");
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

        void loadContributions();

        return () => abortController.abort();
    }, []);

    async function handleDelete(contribution: Contribution) {
        if (!window.confirm(`Supprimer définitivement « ${contribution.title} » ? Cette action est irréversible.`)) {
            return;
        }
        setPendingId(contribution.id);
        setActionError(null);
        try {
            const response = await apiFetch(`/api/v1/activities/${contribution.id}`, { method: "DELETE" });
            if (response.ok) {
                setContributions((current) => current.filter((item) => item.id !== contribution.id));
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setActionError(body?.message ?? "Impossible de supprimer cette activité, réessaie.");
            }
        } catch {
            setActionError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setPendingId(null);
        }
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Mes contributions</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            <main className="favorites-content">
                {isLoading && <p className="activities-status">Chargement de tes contributions…</p>}
                {!isLoading && loadError && (
                    <p className="activities-status activities-status-error">{loadError}</p>
                )}
                {actionError && <p className="activities-status activities-status-error">{actionError}</p>}
                {!isLoading && !loadError && contributions.length === 0 && (
                    <p className="activities-status">
                        Tu n'as encore proposé aucune activité. Utilise « Proposer une activité » depuis la carte.
                    </p>
                )}
                {!isLoading && !loadError && contributions.length > 0 && (
                    <ul className="favorites-list">
                        {contributions.map((contribution) => (
                            <li className="favorites-list-item contributions-list-item" key={contribution.id}>
                                <div className="favorites-list-item-info">
                                    <span className="favorites-list-item-title">{contribution.title}</span>
                                    <span className="favorites-list-item-meta">
                                        <span className={`contribution-status contribution-status-${contribution.status.toLowerCase()}`}>
                                            {STATUS_LABELS[contribution.status] ?? contribution.status}
                                        </span>
                                        {" · "}
                                        {formatWhen(contribution)}
                                        {contribution.city ? ` · ${contribution.city}` : ""}
                                    </span>
                                </div>
                                <div className="contributions-list-item-actions">
                                    {EDITABLE_STATUSES.has(contribution.status) && (
                                        <Link className="contribution-action" to={`/?edit=${contribution.id}`}>
                                            Modifier
                                        </Link>
                                    )}
                                    <button
                                        className="contribution-action contribution-action-danger"
                                        disabled={pendingId === contribution.id}
                                        onClick={() => void handleDelete(contribution)}
                                        type="button"
                                    >
                                        {pendingId === contribution.id ? "Suppression…" : "Supprimer"}
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </main>
        </div>
    );
}

export default MyContributionsPage;
