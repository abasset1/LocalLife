import { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../../api/apiClient";
import { PLACE_TYPE_OPTIONS, placeTypeLabel } from "../places/placeTypes";
import type { AdminPlace, AdminPlacePage, PlaceFlag, PlaceImportReport, PlaceStatus } from "../places/types";

/**
 * Lieux permanents (ADR-0011, phase B) : source DATAtourisme des lieux (essai à blanc, activation vérifiée,
 * import) et file de modération. Tout lieu importé arrive « En attente » ; rien n'est publié sans action d'un
 * administrateur. Le backend applique les règles (transitions permises, liste blanche, garde-fous).
 */

interface PlaceSource {
    id: number;
    name: string;
    type: string;
    status: string;
    lastSyncAt: string | null;
}

interface ApiErrorBody {
    message?: string;
}

const PLACE_SOURCE_TYPE = "DATATOURISME_LIEUX";
const PAGE_SIZE = 50;

const STATUS_TABS: { value: PlaceStatus; label: string }[] = [
    { value: "PENDING", label: "En attente" },
    { value: "PUBLISHED", label: "Publiés" },
    { value: "REJECTED", label: "Refusés" },
    { value: "ARCHIVED", label: "Archivés" },
];

export const FLAG_LABELS: Record<PlaceFlag, string> = {
    NO_ADDRESS: "Sans adresse",
    ROUTE_NAME: "Itinéraire ?",
    DUPLICATE_NAME: "Doublon possible",
    APPROXIMATE_POSITION: "Emplacement approximatif",
};

const VERDICT_LABELS: Record<PlaceImportReport["verdict"], string> = {
    OK: "Réussi",
    SUSPICIOUS: "Résultat suspect",
    FAILED: "Échec",
};

function reportSummary(report: PlaceImportReport): string {
    const parts = [
        `${report.collected} lieu(x) lu(s)`,
        `${report.whitelisted} retenu(s) par la liste blanche`,
        `${report.created} ${report.dryRun ? "à créer" : "créé(s)"} en attente`,
        `${report.updated} ${report.dryRun ? "à mettre à jour" : "mis à jour"}`,
    ];
    if (report.backToPending > 0) {
        parts.push(`${report.backToPending} repassé(s) en attente`);
    }
    if (report.reappeared > 0) {
        parts.push(`${report.reappeared} réapparu(s)`);
    }
    parts.push(`${report.archived} ${report.dryRun ? "à archiver" : "archivé(s)"}`);
    if (report.archiveWithheld > 0) {
        parts.push(`${report.archiveWithheld} retenu(s) par le garde-fou`);
    }
    if (report.rejectedKept > 0) {
        parts.push(`${report.rejectedKept} refusé(s) laissé(s) refusé(s)`);
    }
    return parts.join(", ");
}

async function errorMessage(response: Response, fallback: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
    return body?.message ?? fallback;
}

function AdminPlacesSection() {
    // --- Source des lieux ---
    const [sources, setSources] = useState<PlaceSource[]>([]);
    const [sourcesRefreshKey, setSourcesRefreshKey] = useState(0);
    const [busySourceId, setBusySourceId] = useState<number | null>(null);
    const [reports, setReports] = useState<Record<number, PlaceImportReport>>({});
    const [sourceError, setSourceError] = useState<string | null>(null);

    // --- File de modération ---
    const [status, setStatus] = useState<PlaceStatus>("PENDING");
    const [placeType, setPlaceType] = useState("");
    const [department, setDepartment] = useState("");
    const [flagged, setFlagged] = useState<"" | "true" | "false">("");
    const [offset, setOffset] = useState(0);
    const [page, setPage] = useState<AdminPlacePage | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const [busy, setBusy] = useState(false);
    const [actionMessage, setActionMessage] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        const abortController = new AbortController();
        async function loadSources() {
            try {
                const response = await apiFetch("/api/v1/sources", { signal: abortController.signal });
                if (response.ok) {
                    const all = (await response.json()) as PlaceSource[];
                    setSources(all.filter((source) => source.type === PLACE_SOURCE_TYPE));
                }
            } catch {
                // Annulé ou serveur injoignable : la file de modération reste utilisable.
            }
        }
        void loadSources();
        return () => abortController.abort();
    }, [sourcesRefreshKey]);

    const query = useMemo(() => {
        const params = new URLSearchParams({ status, limit: String(PAGE_SIZE), offset: String(offset) });
        if (placeType) {
            params.set("type", placeType);
        }
        if (department.trim()) {
            params.set("department", department.trim());
        }
        if (flagged) {
            params.set("flagged", flagged);
        }
        return params.toString();
    }, [status, placeType, department, flagged, offset]);

    useEffect(() => {
        const abortController = new AbortController();
        async function loadPlaces() {
            setIsLoading(true);
            setLoadError(null);
            try {
                const response = await apiFetch(`/api/v1/admin/places?${query}`, { signal: abortController.signal });
                if (response.ok) {
                    setPage((await response.json()) as AdminPlacePage);
                    setSelected(new Set());
                } else {
                    setLoadError(await errorMessage(response, "Impossible de charger les lieux."));
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
        void loadPlaces();
        return () => abortController.abort();
    }, [query, refreshKey]);

    function changeFilter(apply: () => void) {
        apply();
        setOffset(0);
        setActionMessage(null);
    }

    async function handleSourceAction(source: PlaceSource, action: "dry-run" | "activate" | "import") {
        setBusySourceId(source.id);
        setSourceError(null);
        try {
            const response = await apiFetch(`/api/v1/admin/places/sources/${source.id}/${action}`, { method: "POST" });
            if (response.ok || (action === "activate" && response.status === 409)) {
                const report = (await response.json()) as PlaceImportReport;
                setReports((current) => ({ ...current, [source.id]: report }));
                if (action !== "dry-run") {
                    setSourcesRefreshKey((key) => key + 1);
                    setRefreshKey((key) => key + 1);
                }
            } else {
                setSourceError(await errorMessage(response, "Action impossible sur cette source."));
            }
        } catch {
            setSourceError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setBusySourceId(null);
        }
    }

    async function moderateOne(place: AdminPlace, action: "publish" | "reject") {
        setBusy(true);
        setActionMessage(null);
        try {
            const response = await apiFetch(`/api/v1/admin/places/${place.id}/${action}`, { method: "PATCH" });
            if (response.ok) {
                setActionMessage(`« ${place.name} » ${action === "publish" ? "publié" : "refusé"}.`);
                setRefreshKey((key) => key + 1);
            } else {
                setActionMessage(await errorMessage(response, "Action impossible sur ce lieu."));
            }
        } catch {
            setActionMessage("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setBusy(false);
        }
    }

    async function moderateSelection(action: "PUBLISH" | "REJECT") {
        const ids = [...selected];
        const verb = action === "PUBLISH" ? "Publier" : "Refuser";
        if (ids.length === 0 || !window.confirm(`${verb} ${ids.length} lieu(x) ?`)) {
            return;
        }
        setBusy(true);
        setActionMessage(null);
        try {
            const response = await apiFetch("/api/v1/admin/places/moderation", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ ids, action }),
            });
            if (response.ok) {
                const result = (await response.json()) as { requested: number[]; applied: number[] };
                setActionMessage(`${result.applied.length} lieu(x) ${action === "PUBLISH" ? "publié(s)" : "refusé(s)"}`
                    + ` sur ${result.requested.length} sélectionné(s).`);
                setRefreshKey((key) => key + 1);
            } else {
                setActionMessage(await errorMessage(response, "Action groupée impossible."));
            }
        } catch {
            setActionMessage("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setBusy(false);
        }
    }

    function toggle(id: number) {
        setSelected((current) => {
            const next = new Set(current);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    const items = page?.items ?? [];
    const allSelected = items.length > 0 && items.every((place) => selected.has(place.id));
    const canPublish = status === "PENDING" || status === "REJECTED";
    const canReject = status === "PENDING" || status === "PUBLISHED";

    return (
        <section aria-labelledby="admin-places-title">
            <h2 id="admin-places-title">Lieux permanents</h2>

            <div className="admin-places-sources">
                {sources.length === 0 && <p className="activities-status">Aucune source de lieux configurée.</p>}
                {sources.map((source) => (
                    <div className="admin-activity-card" key={source.id}>
                        <div className="admin-activity-header">
                            <h3>{source.name}</h3>
                            <span>{source.status === "ACTIVE" ? "Active" : "Inactive"}</span>
                        </div>
                        <p className="admin-activity-description">
                            Dernier import : {source.lastSyncAt ? new Date(source.lastSyncAt).toLocaleString("fr-FR") : "jamais"}.
                            {source.status !== "ACTIVE" && " Lance un essai à blanc, puis active la source s'il est réussi."}
                        </p>
                        <div className="admin-activity-actions">
                            <button
                                disabled={busySourceId === source.id}
                                onClick={() => void handleSourceAction(source, "dry-run")}
                                type="button"
                            >
                                Essai à blanc
                            </button>
                            {source.status !== "ACTIVE" ? (
                                <button
                                    className="admin-approve-button"
                                    disabled={busySourceId === source.id}
                                    onClick={() => void handleSourceAction(source, "activate")}
                                    type="button"
                                >
                                    Activer après vérification
                                </button>
                            ) : (
                                <button
                                    className="admin-approve-button"
                                    disabled={busySourceId === source.id}
                                    onClick={() => void handleSourceAction(source, "import")}
                                    type="button"
                                >
                                    Importer maintenant
                                </button>
                            )}
                        </div>
                        {busySourceId === source.id && (
                            <p className="activities-status" role="status">Lecture du fichier DATAtourisme…</p>
                        )}
                        {reports[source.id] && (
                            <div className="admin-dry-run-report" role="status">
                                <p>
                                    <strong>
                                        {reports[source.id].dryRun ? "Essai à blanc" : "Import"} :{" "}
                                        {VERDICT_LABELS[reports[source.id].verdict]}
                                    </strong>{" "}
                                    — {reportSummary(reports[source.id])}.
                                    {reports[source.id].dryRun && " Aucune écriture."}
                                </p>
                                {reports[source.id].problems.length > 0 && (
                                    <ul>
                                        {reports[source.id].problems.map((problem) => <li key={problem}>{problem}</li>)}
                                    </ul>
                                )}
                            </div>
                        )}
                    </div>
                ))}
                {sourceError && <p className="activities-status activities-status-error" role="alert">{sourceError}</p>}
            </div>

            <nav aria-label="Statut des lieux" className="admin-tabs">
                {STATUS_TABS.map((tab) => (
                    <button
                        className={tab.value === status ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={tab.value}
                        onClick={() => changeFilter(() => setStatus(tab.value))}
                        type="button"
                    >
                        {tab.label}
                    </button>
                ))}
            </nav>

            <div className="admin-places-filters">
                <label>
                    Type
                    <select onChange={(event) => changeFilter(() => setPlaceType(event.target.value))} value={placeType}>
                        <option value="">Tous les types</option>
                        {PLACE_TYPE_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                    </select>
                </label>
                <label>
                    Département
                    <input
                        inputMode="numeric"
                        maxLength={3}
                        onChange={(event) => changeFilter(() => setDepartment(event.target.value))}
                        placeholder="ex. 84"
                        value={department}
                    />
                </label>
                <label>
                    Signalements
                    <select
                        onChange={(event) => changeFilter(() => setFlagged(event.target.value as "" | "true" | "false"))}
                        value={flagged}
                    >
                        <option value="">Tous</option>
                        <option value="true">Seulement les lieux signalés</option>
                        <option value="false">Seulement les lieux sans signalement</option>
                    </select>
                </label>
            </div>

            {page && (
                <div className="admin-places-bulk">
                    <span>
                        {page.total} lieu(x) · {selected.size} sélectionné(s)
                    </span>
                    <label>
                        <input
                            checked={allSelected}
                            disabled={items.length === 0}
                            onChange={() => setSelected(allSelected ? new Set() : new Set(items.map((place) => place.id)))}
                            type="checkbox"
                        />{" "}
                        Toute la page
                    </label>
                    {canPublish && (
                        <button
                            className="admin-approve-button"
                            disabled={busy || selected.size === 0}
                            onClick={() => void moderateSelection("PUBLISH")}
                            type="button"
                        >
                            Publier la sélection
                        </button>
                    )}
                    {canReject && (
                        <button
                            className="admin-reject-button"
                            disabled={busy || selected.size === 0}
                            onClick={() => void moderateSelection("REJECT")}
                            type="button"
                        >
                            Refuser la sélection
                        </button>
                    )}
                </div>
            )}
            {actionMessage && <p className="activities-status" role="status">{actionMessage}</p>}
            {isLoading && <p className="activities-status">Chargement des lieux…</p>}
            {!isLoading && loadError && <p className="activities-status activities-status-error" role="alert">{loadError}</p>}
            {!isLoading && !loadError && items.length === 0 && (
                <p className="activities-status">Aucun lieu pour ces critères.</p>
            )}

            <ul className="admin-activity-list">
                {items.map((place) => (
                    <li className="admin-activity-card" key={place.id}>
                        <div className="admin-activity-header">
                            <label className="admin-place-select">
                                <input
                                    aria-label={`Sélectionner ${place.name}`}
                                    checked={selected.has(place.id)}
                                    onChange={() => toggle(place.id)}
                                    type="checkbox"
                                />
                                <h3>{place.name}</h3>
                            </label>
                            <span>{placeTypeLabel(place.placeType)}</span>
                        </div>
                        {place.flags.length > 0 && (
                            <ul aria-label="Signalements du lieu" className="admin-place-flags">
                                {place.flags.map((flag) => (
                                    <li className="admin-place-flag" key={flag}>{FLAG_LABELS[flag]}</li>
                                ))}
                            </ul>
                        )}
                        <dl className="admin-activity-meta">
                            <dt>Adresse</dt>
                            <dd>{[place.address, place.postalCode, place.city].filter(Boolean).join(", ") || "—"}</dd>
                            {place.latitude !== null && place.longitude !== null && (
                                <>
                                    <dt>Position</dt>
                                    <dd>
                                        <a
                                            href={`https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=18/${place.latitude}/${place.longitude}`}
                                            rel="noreferrer"
                                            target="_blank"
                                        >
                                            Voir sur la carte
                                        </a>
                                    </dd>
                                </>
                            )}
                            {place.website && (
                                <>
                                    <dt>Site</dt>
                                    <dd>
                                        <a href={place.website} rel="noreferrer" target="_blank">{place.website}</a>
                                    </dd>
                                </>
                            )}
                            {place.sourceName && (
                                <>
                                    <dt>Source</dt>
                                    <dd>{place.sourceName}</dd>
                                </>
                            )}
                        </dl>
                        {place.description && (
                            <p className="admin-activity-description">
                                {place.description.length > 300 ? `${place.description.slice(0, 300)}…` : place.description}
                            </p>
                        )}
                        <div className="admin-activity-actions">
                            {canPublish && (
                                <button
                                    className="admin-approve-button"
                                    disabled={busy}
                                    onClick={() => void moderateOne(place, "publish")}
                                    type="button"
                                >
                                    Publier
                                </button>
                            )}
                            {canReject && (
                                <button
                                    className="admin-reject-button"
                                    disabled={busy}
                                    onClick={() => void moderateOne(place, "reject")}
                                    type="button"
                                >
                                    Refuser
                                </button>
                            )}
                        </div>
                    </li>
                ))}
            </ul>

            {page && page.total > PAGE_SIZE && (
                <nav aria-label="Pages" className="admin-places-pagination">
                    <button disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))} type="button">
                        ← Précédents
                    </button>
                    <span>
                        {offset + 1}–{Math.min(offset + PAGE_SIZE, page.total)} sur {page.total}
                    </span>
                    <button
                        disabled={offset + PAGE_SIZE >= page.total}
                        onClick={() => setOffset(offset + PAGE_SIZE)}
                        type="button"
                    >
                        Suivants →
                    </button>
                </nav>
            )}
        </section>
    );
}

export default AdminPlacesSection;
