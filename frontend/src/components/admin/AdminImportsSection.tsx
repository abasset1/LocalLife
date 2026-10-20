import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "../../api/apiClient";
import AdminAgendaDiscovery from "./AdminAgendaDiscovery";

/** `SyncStatus` (backend) : seule `SUCCESS` signifie une collecte complète. */
type SyncStatus = "SUCCESS" | "PARTIAL" | "FAILED";

/** `ImportResult` (backend) : bilan d'import d'une source. */
interface ImportResult {
    sourceName: string;
    startedAt: string;
    endedAt: string;
    fetched: number;
    created: number;
    updated: number;
    unchanged: number;
    archived: number;
    ignored: number;
    duplicates: number;
    needsReview: number;
    errors: number;
    /** Activités disparues non archivées : disparition massive jugée suspecte (garde-fou backend). */
    archiveWithheld: number;
    /** Pourquoi l'import de cette source a été bloqué (collecte en échec, archivage suspendu), sinon `null`. */
    blockedReason: string | null;
    /** `PARTIAL` : collecte arrêtée avant la fin (rien n'est archivé) ; `FAILED` : rien collecté. */
    status: SyncStatus;
    pagesRead: number;
    /** Éléments bruts reçus de la source, avant les filtres du collecteur. */
    received: number;
    stopReason: string | null;
}

/** `SourceSyncState` (backend) : dernier essai et dernier import complet d'une source. */
interface SourceSyncState {
    sourceId: number;
    sourceName: string;
    sourceStatus: string;
    lastAttemptAt: string | null;
    lastStatus: SyncStatus | null;
    lastMessage: string | null;
    lastPagesRead: number;
    lastReceived: number;
    lastDurationMs: number;
    lastSuccessAt: string | null;
}

const STATUS_LABELS: Record<SyncStatus, string> = {
    SUCCESS: "Complet",
    PARTIAL: "Partiel",
    FAILED: "Échec",
};

function formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString("fr-FR") : "—";
}

interface AdminImportsSectionProps {
    /** Des sources ont été créées (découverte) : la liste des agendas doit être rechargée. */
    onSourcesChanged: () => void;
}

type NumericKey = {
    [K in keyof ImportResult]: ImportResult[K] extends number ? K : never;
}[keyof ImportResult];

const RESULT_COLUMNS: { key: NumericKey; label: string; title: string }[] = [
    { key: "pagesRead", label: "Pages", title: "Pages lues auprès de la source" },
    { key: "received", label: "Reçues", title: "Éléments bruts reçus, avant les filtres (lieu, région, dates passées)" },
    { key: "fetched", label: "Collectées", title: "Activités retenues après les filtres du collecteur" },
    { key: "created", label: "Créées", title: "Nouvelles activités" },
    { key: "updated", label: "Mises à jour", title: "Activités existantes modifiées" },
    { key: "unchanged", label: "Inchangées", title: "Déjà à jour" },
    { key: "archived", label: "Archivées", title: "Activités disparues de la source" },
    {
        key: "archiveWithheld",
        label: "Archivage suspendu",
        title: "Activités disparues non archivées : disparition massive jugée suspecte, à vérifier",
    },
    { key: "needsReview", label: "À relire", title: "Classification à vérifier" },
    { key: "duplicates", label: "Doublons", title: "Doublons écartés" },
    { key: "ignored", label: "Ignorées", title: "Activités écartées (passées, incomplètes…)" },
    { key: "errors", label: "Erreurs", title: "Échecs de collecte ou de traitement" },
];

async function errorMessage(response: Response, fallback: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    return body?.message ?? fallback;
}

/** Durée écoulée lisible, « 1 min 05 s ». */
function formatElapsed(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return minutes > 0 ? `${minutes} min ${String(rest).padStart(2, "0")} s` : `${rest} s`;
}

/** Secondes écoulées depuis `since` (mis à jour chaque seconde), `0` sans opération en cours. */
function useElapsedSeconds(since: number | null): number {
    const [now, setNow] = useState(() => Date.now());
    useEffect(() => {
        if (since === null) {
            return;
        }
        const timer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(timer);
    }, [since]);
    return since === null ? 0 : Math.max(0, Math.round((now - since) / 1000));
}

/**
 * Section « Imports » de l'administration : découverte des agendas OpenAgenda et sélection des sources
 * ({@link AdminAgendaDiscovery}), import des activités de toutes les sources actives
 * (`POST /api/v1/admin/import`, bilan par source avec statut complet / partiel / échec) et état de
 * synchronisation de chaque source (`GET /api/v1/admin/import/sync-states`).
 */
function AdminImportsSection({ onSourcesChanged }: AdminImportsSectionProps) {
    const [importStartedAt, setImportStartedAt] = useState<number | null>(null);
    const [importResults, setImportResults] = useState<ImportResult[] | null>(null);
    const [importError, setImportError] = useState<string | null>(null);
    const [syncStates, setSyncStates] = useState<SourceSyncState[] | null>(null);

    const importElapsed = useElapsedSeconds(importStartedAt);

    const loadSyncStates = useCallback(async () => {
        try {
            const response = await apiFetch("/api/v1/admin/import/sync-states");
            if (response.ok) {
                setSyncStates((await response.json()) as SourceSyncState[]);
            }
        } catch {
            // Tableau d'état simplement absent : le bilan de l'import reste affiché.
        }
    }, []);

    useEffect(() => {
        void loadSyncStates();
    }, [loadSyncStates]);

    async function handleImport() {
        if (!window.confirm("Lancer l'import des activités de toutes les sources actives ? Cela peut prendre plusieurs minutes.")) {
            return;
        }
        setImportError(null);
        setImportResults(null);
        setImportStartedAt(Date.now());
        try {
            const response = await apiFetch("/api/v1/admin/import", { method: "POST" });
            if (!response.ok) {
                setImportError(
                    response.status === 409
                        ? "Un import est déjà en cours (planifié ou lancé par un autre administrateur). Réessaie quand il sera terminé."
                        : await errorMessage(response, "L'import a échoué."),
                );
                return;
            }
            setImportResults((await response.json()) as ImportResult[]);
        } catch {
            setImportError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setImportStartedAt(null);
            void loadSyncStates();
        }
    }

    const total = (key: NumericKey) => (importResults ?? []).reduce((sum, result) => sum + result[key], 0);
    const countStatus = (status: SyncStatus) => (importResults ?? []).filter((result) => result.status === status).length;

    return (
        <div className="admin-imports">
            <AdminAgendaDiscovery onSourcesChanged={onSourcesChanged} />

            <section aria-labelledby="admin-import-title" className="admin-import-card">
                <h2 id="admin-import-title">Importer les activités</h2>
                <p className="admin-import-hint">
                    Collecte les activités de toutes les sources actives, puis les crée, les met à jour ou archive
                    celles qui ont disparu. Une source n'est déclarée complète que si toutes ses pages ont été
                    lues ; une collecte partielle importe ce qu'elle a reçu mais n'archive rien. Un seul import
                    peut tourner à la fois (y compris l'import planifié).
                </p>
                <button
                    className="admin-import-button"
                    disabled={importStartedAt !== null}
                    onClick={() => void handleImport()}
                    type="button"
                >
                    {importStartedAt !== null ? (
                        <>
                            <span aria-hidden="true" className="admin-import-spinner" />
                            Import en cours… {formatElapsed(importElapsed)}
                        </>
                    ) : (
                        "Lancer l'import"
                    )}
                </button>
                {importStartedAt !== null && (
                    <p className="admin-import-hint" role="status">
                        Tu peux changer d'onglet : le bilan s'affichera ici à la fin de l'import.
                    </p>
                )}
                {importError && (
                    <p className="admin-import-message admin-import-message-error" role="alert">
                        {importError}
                    </p>
                )}
                {importResults && importResults.length === 0 && (
                    <p className="admin-import-message" role="status">
                        Aucune source à importer : ajoute ou active un agenda dans l'onglet « Agendas ».
                    </p>
                )}
                {importResults && importResults.length > 0 && (
                    <div className="admin-import-results" role="status">
                        <p
                            className={`admin-import-message ${
                                countStatus("FAILED") > 0
                                    ? "admin-import-message-error"
                                    : countStatus("PARTIAL") > 0 || total("errors") > 0 || total("archiveWithheld") > 0
                                      ? "admin-import-message-warning"
                                      : "admin-import-message-success"
                            }`}
                        >
                            {countStatus("PARTIAL") + countStatus("FAILED") > 0
                                ? `Import terminé avec ${countStatus("PARTIAL")} collecte(s) partielle(s) et ${countStatus("FAILED")} en échec : ces sources ne sont pas à jour en entier. `
                                : "Import terminé, toutes les collectes sont complètes : "}
                            <strong>{total("created")} activité(s) créée(s)</strong>,{" "}
                            {total("updated")} mise(s) à jour, {total("archived")} archivée(s)
                            {total("errors") > 0 ? `, ${total("errors")} erreur(s)` : ""}
                            {total("archiveWithheld") > 0
                                ? `, archivage suspendu pour ${total("archiveWithheld")} activité(s) (disparition massive à vérifier)`
                                : ""}{" "}
                            sur {importResults.length}{" "}
                            source(s).
                        </p>
                        {importResults.some((result) => result.blockedReason) && (
                            <ul className="admin-import-blocked">
                                {importResults
                                    .filter((result) => result.blockedReason)
                                    .map((result) => (
                                        <li key={result.sourceName}>
                                            <strong>{result.sourceName}</strong> : {result.blockedReason}
                                        </li>
                                    ))}
                            </ul>
                        )}
                        <div className="admin-import-table-wrapper">
                            <table className="admin-import-table">
                                <thead>
                                    <tr>
                                        <th scope="col">Source</th>
                                        <th scope="col" title="Complet : toutes les pages lues ; partiel : arrêt avant la fin, rien archivé">
                                            Statut
                                        </th>
                                        {RESULT_COLUMNS.map((column) => (
                                            <th key={column.key} scope="col" title={column.title}>
                                                {column.label}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {importResults.map((result) => (
                                        <tr
                                            className={result.errors > 0 || result.status !== "SUCCESS" ? "has-errors" : undefined}
                                            key={result.sourceName}
                                        >
                                            <th scope="row">{result.sourceName}</th>
                                            <td className={`admin-sync-status admin-sync-status-${result.status.toLowerCase()}`}>
                                                {STATUS_LABELS[result.status]}
                                            </td>
                                            {RESULT_COLUMNS.map((column) => (
                                                <td key={column.key}>{result[column.key]}</td>
                                            ))}
                                        </tr>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <th scope="row">Total</th>
                                        <td />
                                        {RESULT_COLUMNS.map((column) => (
                                            <td key={column.key}>{total(column.key)}</td>
                                        ))}
                                    </tr>
                                </tfoot>
                            </table>
                        </div>
                    </div>
                )}
            </section>

            <section aria-labelledby="admin-sync-title" className="admin-import-card">
                <h2 id="admin-sync-title">État de synchronisation des sources</h2>
                <p className="admin-import-hint">
                    Dernier essai d'import de chaque source et date du dernier import complet. Une source
                    « Partiel » ou « Échec » garde ses activités déjà importées.
                </p>
                {syncStates && syncStates.length === 0 && <p className="admin-import-hint">Aucune source collectable.</p>}
                {syncStates && syncStates.length > 0 && (
                    <div className="admin-import-table-wrapper">
                        <table className="admin-import-table admin-sync-table">
                            <thead>
                                <tr>
                                    <th scope="col">Source</th>
                                    <th scope="col">Dernier essai</th>
                                    <th scope="col">Statut</th>
                                    <th scope="col">Dernier import complet</th>
                                    <th scope="col">Pages</th>
                                    <th scope="col">Reçues</th>
                                    <th scope="col">Détail</th>
                                </tr>
                            </thead>
                            <tbody>
                                {syncStates.map((state) => (
                                    <tr key={state.sourceId}>
                                        <th scope="row">
                                            {state.sourceName}
                                            {state.sourceStatus !== "ACTIVE" ? " (inactive)" : ""}
                                        </th>
                                        <td>{formatDateTime(state.lastAttemptAt)}</td>
                                        <td
                                            className={
                                                state.lastStatus
                                                    ? `admin-sync-status admin-sync-status-${state.lastStatus.toLowerCase()}`
                                                    : undefined
                                            }
                                        >
                                            {state.lastStatus ? STATUS_LABELS[state.lastStatus] : "Jamais importée"}
                                        </td>
                                        <td>{formatDateTime(state.lastSuccessAt)}</td>
                                        <td>{state.lastPagesRead}</td>
                                        <td>{state.lastReceived}</td>
                                        <td className="admin-sync-message">{state.lastMessage ?? ""}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}

export default AdminImportsSection;
