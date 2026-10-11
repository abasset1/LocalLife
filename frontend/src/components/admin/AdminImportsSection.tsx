import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";

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
}

/** `AgendaDiscoveryResult` (backend). */
interface AgendaDiscoveryResult {
    listed: number;
    created: number;
    alreadyKnown: number;
    createdNames: string[];
}

interface AdminImportsSectionProps {
    /** Des sources ont été créées (découverte) : la liste des agendas doit être rechargée. */
    onSourcesChanged: () => void;
}

const RESULT_COLUMNS: { key: keyof ImportResult; label: string; title: string }[] = [
    { key: "fetched", label: "Collectées", title: "Activités renvoyées par la source" },
    { key: "created", label: "Créées", title: "Nouvelles activités" },
    { key: "updated", label: "Mises à jour", title: "Activités existantes modifiées" },
    { key: "unchanged", label: "Inchangées", title: "Déjà à jour" },
    { key: "archived", label: "Archivées", title: "Activités disparues de la source" },
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
 * Section « Imports » de l'administration : découverte des agendas OpenAgenda officiels
 * (`POST /api/v1/admin/import/discover-agendas`, crée les sources manquantes) et import des
 * activités de toutes les sources actives (`POST /api/v1/admin/import`, bilan par source).
 */
function AdminImportsSection({ onSourcesChanged }: AdminImportsSectionProps) {
    const [discoveryStartedAt, setDiscoveryStartedAt] = useState<number | null>(null);
    const [discoveryResult, setDiscoveryResult] = useState<AgendaDiscoveryResult | null>(null);
    const [discoveryError, setDiscoveryError] = useState<string | null>(null);

    const [importStartedAt, setImportStartedAt] = useState<number | null>(null);
    const [importResults, setImportResults] = useState<ImportResult[] | null>(null);
    const [importError, setImportError] = useState<string | null>(null);

    const discoveryElapsed = useElapsedSeconds(discoveryStartedAt);
    const importElapsed = useElapsedSeconds(importStartedAt);

    async function handleDiscover(event: FormEvent) {
        event.preventDefault();
        setDiscoveryError(null);
        setDiscoveryResult(null);
        setDiscoveryStartedAt(Date.now());
        try {
            const response = await apiFetch("/api/v1/admin/import/discover-agendas", { method: "POST" });
            if (!response.ok) {
                setDiscoveryError(await errorMessage(response, "La découverte des agendas a échoué."));
                return;
            }
            const result = (await response.json()) as AgendaDiscoveryResult;
            setDiscoveryResult(result);
            if (result.created > 0) {
                onSourcesChanged();
            }
        } catch {
            setDiscoveryError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setDiscoveryStartedAt(null);
        }
    }

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
        }
    }

    const total = (key: keyof ImportResult) =>
        (importResults ?? []).reduce((sum, result) => sum + (result[key] as number), 0);

    return (
        <div className="admin-imports">
            <section aria-labelledby="admin-discovery-title" className="admin-import-card">
                <h2 id="admin-discovery-title">Découvrir les agendas OpenAgenda</h2>
                <p className="admin-import-hint">
                    Parcourt tout l'annuaire des agendas officiels OpenAgenda accessible à LocalLife et crée une
                    source pour chaque agenda qui n'en a pas encore. Aucune activité n'est importée à cette étape :
                    les nouveaux agendas seront collectés au prochain import.
                </p>
                <form className="admin-import-form" onSubmit={(event) => void handleDiscover(event)}>
                    <button className="admin-import-button" disabled={discoveryStartedAt !== null} type="submit">
                        {discoveryStartedAt !== null ? (
                            <>
                                <span aria-hidden="true" className="admin-import-spinner" />
                                Découverte en cours… {formatElapsed(discoveryElapsed)}
                            </>
                        ) : (
                            "Découvrir tous les agendas"
                        )}
                    </button>
                </form>
                {discoveryError && (
                    <p className="admin-import-message admin-import-message-error" role="alert">
                        {discoveryError}
                    </p>
                )}
                {discoveryResult && (
                    <div className="admin-import-message admin-import-message-success" role="status">
                        <p>
                            {discoveryResult.listed} agenda(s) examiné(s) : <strong>{discoveryResult.created} nouveau(x)</strong>,{" "}
                            {discoveryResult.alreadyKnown} déjà connu(s).
                        </p>
                        {discoveryResult.createdNames.length > 0 && (
                            <>
                                <p>Agendas ajoutés (visibles dans l'onglet « Agendas ») :</p>
                                <ul className="admin-import-names">
                                    {discoveryResult.createdNames.map((name) => (
                                        <li key={name}>{name}</li>
                                    ))}
                                </ul>
                            </>
                        )}
                    </div>
                )}
            </section>

            <section aria-labelledby="admin-import-title" className="admin-import-card">
                <h2 id="admin-import-title">Importer les activités</h2>
                <p className="admin-import-hint">
                    Collecte les activités de toutes les sources actives, puis les crée, les met à jour ou archive
                    celles qui ont disparu. Un seul import peut tourner à la fois (y compris l'import planifié).
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
                                total("errors") > 0 ? "admin-import-message-warning" : "admin-import-message-success"
                            }`}
                        >
                            Import terminé : <strong>{total("created")} activité(s) créée(s)</strong>,{" "}
                            {total("updated")} mise(s) à jour, {total("archived")} archivée(s)
                            {total("errors") > 0 ? `, ${total("errors")} erreur(s)` : ""} sur {importResults.length}{" "}
                            source(s).
                        </p>
                        <div className="admin-import-table-wrapper">
                            <table className="admin-import-table">
                                <thead>
                                    <tr>
                                        <th scope="col">Source</th>
                                        {RESULT_COLUMNS.map((column) => (
                                            <th key={column.key} scope="col" title={column.title}>
                                                {column.label}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {importResults.map((result) => (
                                        <tr className={result.errors > 0 ? "has-errors" : undefined} key={result.sourceName}>
                                            <th scope="row">{result.sourceName}</th>
                                            {RESULT_COLUMNS.map((column) => (
                                                <td key={column.key}>{result[column.key]}</td>
                                            ))}
                                        </tr>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <th scope="row">Total</th>
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
        </div>
    );
}

export default AdminImportsSection;
