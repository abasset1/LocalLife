import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";
import { confirmHeaders, useConfirmDialog } from "./useConfirmDialog";

interface SourceOption {
    id: number;
    name: string;
}

const STATUS_OPTIONS = [
    { value: "", label: "Tous les statuts" },
    { value: "PENDING", label: "En attente" },
    { value: "PUBLISHED", label: "Publiées" },
    { value: "REJECTED", label: "Refusées" },
    { value: "CANCELLED", label: "Annulées" },
    { value: "ARCHIVED", label: "Archivées" },
];

/** Mot à saisir pour confirmer la suppression de toutes les activités. */
const CONFIRM_ALL_WORD = "SUPPRIMER";

interface AdminDeletionSectionProps {
    /** Des activités ont été supprimées : la modération doit être rechargée. */
    onActivitiesDeleted: () => void;
}

async function errorMessage(response: Response, fallback: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    return body?.message ?? fallback;
}

function plural(count: number): string {
    return `${count} activité${count > 1 ? "s" : ""}`;
}

/**
 * Section « Suppression » de l'administration : met à la corbeille (30 jours) les activités qui répondent
 * à des critères (source, statut, période de début), ou toutes. Toujours en deux temps : un aperçu
 * compte les activités visées, puis la suppression reprend ce nombre — le serveur refuse si les
 * activités visées ont changé entre-temps (`DELETE /api/v1/admin/activities`). La suppression exige un
 * motif et le mot de passe de l'administrateur (`X-Confirm-Password`, 428 sinon) ; le lot supprimé se
 * restaure d'un clic.
 */
function AdminDeletionSection({ onActivitiesDeleted }: AdminDeletionSectionProps) {
    const [sources, setSources] = useState<SourceOption[]>([]);
    const [sourceId, setSourceId] = useState("");
    const [status, setStatus] = useState("");
    const [startFrom, setStartFrom] = useState("");
    const [startTo, setStartTo] = useState("");
    const [all, setAll] = useState(false);

    /** Aperçu valable pour les critères affichés ; effacé dès qu'un critère change. */
    const [preview, setPreview] = useState<{ query: string; count: number } | null>(null);
    const [confirmWord, setConfirmWord] = useState("");
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    /** Dernier lot mis à la corbeille, restaurable tant qu'il n'est pas purgé. */
    const [lastBatch, setLastBatch] = useState<string | null>(null);
    const { ask, dialog } = useConfirmDialog();

    useEffect(() => {
        apiFetch("/api/v1/sources")
            .then((response) => (response.ok ? response.json() : []))
            .then((data: SourceOption[]) => setSources(data))
            .catch(() => setSources([]));
    }, []);

    const params = new URLSearchParams();
    if (all) {
        params.set("all", "true");
    } else {
        if (sourceId) params.set("sourceId", sourceId);
        if (status) params.set("status", status);
        if (startFrom) params.set("startFrom", startFrom);
        if (startTo) params.set("startTo", startTo);
    }
    const query = params.toString();
    const hasCriteria = all || [sourceId, status, startFrom, startTo].some(Boolean);
    const currentPreview = preview?.query === query ? preview : null;
    const confirmed = !all || confirmWord.trim().toUpperCase() === CONFIRM_ALL_WORD;

    function resetFeedback() {
        setError(null);
        setSuccess(null);
        setConfirmWord("");
    }

    async function handlePreview(event: FormEvent) {
        event.preventDefault();
        resetFeedback();
        setIsBusy(true);
        try {
            const response = await apiFetch(`/api/v1/admin/activities/deletion-preview?${query}`);
            if (!response.ok) {
                setError(await errorMessage(response, "Impossible de compter les activités visées."));
                return;
            }
            const body = (await response.json()) as { count: number };
            setPreview({ query, count: body.count });
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsBusy(false);
        }
    }

    async function handleDelete() {
        if (!currentPreview || currentPreview.count === 0 || !confirmed) {
            return;
        }
        const confirmation = await ask({
            title: `Supprimer ${plural(currentPreview.count)}`,
            message: `${plural(currentPreview.count)} ${currentPreview.count > 1 ? "seront mises" : "sera mise"} à la
                corbeille : restaurables pendant 30 jours, puis purgées définitivement.`,
            reason: { label: "Motif de la suppression (journalisé)" },
            password: true,
            confirmLabel: "Mettre à la corbeille",
        });
        if (!confirmation) {
            return;
        }
        setError(null);
        setLastBatch(null);
        setIsBusy(true);
        try {
            const reason = encodeURIComponent(confirmation.reason);
            const response = await apiFetch(
                `/api/v1/admin/activities?${query}&expectedCount=${currentPreview.count}&reason=${reason}`,
                { method: "DELETE", headers: confirmHeaders(confirmation.password) },
            );
            if (!response.ok) {
                setError(await errorMessage(response, "La suppression a échoué."));
                // Mot de passe manquant ou faux (428) : l'aperçu reste valable, on peut réessayer.
                if (response.status !== 428) {
                    setPreview(null);
                }
                return;
            }
            const body = (await response.json()) as { count: number; batch: string | null };
            setSuccess(`${plural(body.count)} mise${body.count > 1 ? "s" : ""} à la corbeille`
                + `${body.batch ? ` (lot ${body.batch})` : ""}.`);
            setLastBatch(body.batch);
            setPreview(null);
            setConfirmWord("");
            onActivitiesDeleted();
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsBusy(false);
        }
    }

    async function handleRestoreBatch() {
        if (!lastBatch) {
            return;
        }
        setError(null);
        setIsBusy(true);
        try {
            const response = await apiFetch(`/api/v1/admin/activities/trash/batches/${encodeURIComponent(lastBatch)}/restore`,
                { method: "POST" });
            if (!response.ok) {
                setError(await errorMessage(response, "Impossible de restaurer ce lot."));
                return;
            }
            const body = (await response.json()) as { count: number };
            setSuccess(`${plural(body.count)} restaurée${body.count > 1 ? "s" : ""}.`);
            setLastBatch(null);
            onActivitiesDeleted();
        } catch {
            setError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsBusy(false);
        }
    }

    return (
        <section aria-labelledby="admin-deletion-title" className="admin-import-card admin-deletion">
            <h2 id="admin-deletion-title">Supprimer des activités</h2>
            <p className="admin-import-hint">
                Les activités supprimées vont à la corbeille : restaurables pendant 30 jours, puis purgées
                définitivement avec tout ce qui leur est rattaché (horaires, liens, favoris…). Un motif et ton mot
                de passe sont demandés. Les activités importées encore présentes dans leur agenda reviendront au prochain import : pour
                les écarter durablement, désactive ou supprime aussi l'agenda.
            </p>

            <form className="admin-deletion-form" onSubmit={(event) => void handlePreview(event)}>
                <fieldset disabled={all || isBusy}>
                    <legend>Critères (cumulables)</legend>
                    <label>
                        Agenda / source
                        <select onChange={(event) => setSourceId(event.target.value)} value={sourceId}>
                            <option value="">Toutes les sources</option>
                            {sources.map((source) => (
                                <option key={source.id} value={source.id}>
                                    {source.name}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Statut
                        <select onChange={(event) => setStatus(event.target.value)} value={status}>
                            {STATUS_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Début à partir du
                        <input onChange={(event) => setStartFrom(event.target.value)} type="date" value={startFrom} />
                    </label>
                    <label>
                        Début jusqu'au
                        <input onChange={(event) => setStartTo(event.target.value)} type="date" value={startTo} />
                    </label>
                </fieldset>

                <label className="admin-deletion-all">
                    <input
                        checked={all}
                        disabled={isBusy}
                        onChange={(event) => {
                            setAll(event.target.checked);
                            resetFeedback();
                        }}
                        type="checkbox"
                    />
                    Toutes les activités, sans aucun critère
                </label>

                <button className="admin-import-button" disabled={!hasCriteria || isBusy} type="submit">
                    Compter les activités visées
                </button>
                {!hasCriteria && <p className="admin-import-hint">Choisis au moins un critère, ou « Toutes les activités ».</p>}
            </form>

            {error && (
                <p className="admin-import-message admin-import-message-error" role="alert">
                    {error}
                </p>
            )}
            {success && (
                <div className="admin-import-message admin-import-message-success" role="status">
                    <p>{success}</p>
                    {lastBatch && (
                        <button className="admin-approve-button" disabled={isBusy} onClick={() => void handleRestoreBatch()}
                            type="button">
                            Restaurer ce lot
                        </button>
                    )}
                </div>
            )}

            {currentPreview && currentPreview.count === 0 && (
                <p className="admin-import-message" role="status">
                    Aucune activité ne correspond à ces critères.
                </p>
            )}
            {currentPreview && currentPreview.count > 0 && (
                <div className="admin-import-message admin-import-message-error admin-deletion-confirm" role="status">
                    <p>
                        <strong>{plural(currentPreview.count)}</strong>{" "}
                        {all
                            ? "(toutes les activités de la base)"
                            : currentPreview.count > 1
                              ? "correspondent à ces critères"
                              : "correspond à ces critères"}{" "}
                        {currentPreview.count > 1 ? "seront mises" : "sera mise"} à la corbeille (30 jours).
                    </p>
                    {all && (
                        <label>
                            Pour confirmer, tape <strong>{CONFIRM_ALL_WORD}</strong> :
                            <input
                                autoComplete="off"
                                onChange={(event) => setConfirmWord(event.target.value)}
                                value={confirmWord}
                            />
                        </label>
                    )}
                    <button
                        className="admin-danger-button"
                        disabled={isBusy || !confirmed}
                        onClick={() => void handleDelete()}
                        type="button"
                    >
                        {isBusy ? "Suppression…" : `Supprimer ${plural(currentPreview.count)}`}
                    </button>
                </div>
            )}
            {dialog}
        </section>
    );
}

export default AdminDeletionSection;
