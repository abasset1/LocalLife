import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";

type DiscoveryScope = "ALL" | "OFFICIAL";
type RunStatus = "IN_PROGRESS" | "COMPLETE" | "FAILED" | "ABANDONED";
type CatalogFilter = "NEW" | "KNOWN" | "ALL";

/** `AgendaDiscoveryResult` (backend) : état du parcours de l'annuaire après un lot. */
export interface AgendaDiscoveryResult {
    runId: number;
    scope: DiscoveryScope;
    status: RunStatus;
    /** Vrai seulement si la fin de l'annuaire a été atteinte. */
    complete: boolean;
    /** Vrai si relancer la découverte poursuit ce parcours. */
    resumable: boolean;
    stopReason: string | null;
    pagesThisBatch: number;
    pagesRead: number;
    examined: number;
    newAgendas: number;
    alreadyKnown: number;
    ignored: number;
    errors: number;
    providerTotal: number | null;
    startedAt: string | null;
    updatedAt: string | null;
    endedAt: string | null;
}

/** `DiscoveredAgendaView.Item` (backend). */
interface CatalogItem {
    agendaUid: string;
    title: string | null;
    url: string | null;
    official: boolean | null;
    sourceId: number | null;
    sourceName: string | null;
    sourceStatus: string | null;
}

interface CatalogPage {
    items: CatalogItem[];
    total: number;
    page: number;
    size: number;
}

/** `AdminImportController.DiscoverySettings` (backend). */
interface DiscoverySettings {
    defaultPagesPerBatch: number;
    maxPagesPerBatch: number;
    maxActivationBatch: number;
    maxCatalogPageSize: number;
}

/** `AgendaActivationResult` (backend). */
interface ActivationResult {
    requested: number;
    created: { sourceId: number; name: string; agendaUid: string }[];
    alreadyKnown: string[];
    notFound: string[];
}

const CATALOG_PAGE_SIZE = 50;

const STATUS_LABELS: Record<RunStatus, string> = {
    COMPLETE: "Annuaire lu en entier",
    IN_PROGRESS: "Parcours incomplet, reprenable",
    FAILED: "Parcours en échec : découverte non exhaustive",
    ABANDONED: "Parcours abandonné",
};

const STATUS_CLASSES: Record<RunStatus, string> = {
    COMPLETE: "admin-import-message-success",
    IN_PROGRESS: "admin-import-message-warning",
    FAILED: "admin-import-message-error",
    ABANDONED: "admin-import-message-warning",
};

async function errorMessage(response: Response, fallback: string): Promise<string> {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    return body?.message ?? fallback;
}

interface AdminAgendaDiscoveryProps {
    /** Des sources ont été créées : la liste des agendas doit être rechargée. */
    onSourcesChanged: () => void;
}

/**
 * Découverte des agendas OpenAgenda en deux temps (sprint « agendas exhaustifs ») :
 * 1. parcours de l'annuaire par lots reprenables, qui alimente un catalogue sans créer de source ;
 * 2. aperçu du catalogue et création de sources pour les seuls agendas sélectionnés, par lot borné.
 * Seul le statut « Annuaire lu en entier » signifie que la découverte est exhaustive.
 */
function AdminAgendaDiscovery({ onSourcesChanged }: AdminAgendaDiscoveryProps) {
    const [scope, setScope] = useState<DiscoveryScope>("ALL");
    const [settings, setSettings] = useState<DiscoverySettings | null>(null);
    const [run, setRun] = useState<AgendaDiscoveryResult | null>(null);
    const [running, setRunning] = useState(false);
    const [untilEnd, setUntilEnd] = useState(false);
    const [discoveryError, setDiscoveryError] = useState<string | null>(null);
    const stopRequested = useRef(false);

    const [filter, setFilter] = useState<CatalogFilter>("NEW");
    const [query, setQuery] = useState("");
    const [appliedQuery, setAppliedQuery] = useState("");
    const [page, setPage] = useState(0);
    const [catalog, setCatalog] = useState<CatalogPage | null>(null);
    const [catalogError, setCatalogError] = useState<string | null>(null);
    const [selected, setSelected] = useState<Set<string>>(() => new Set());
    const [activating, setActivating] = useState(false);
    const [activation, setActivation] = useState<ActivationResult | null>(null);
    const [activationError, setActivationError] = useState<string | null>(null);

    useEffect(() => {
        void (async () => {
            try {
                const response = await apiFetch("/api/v1/admin/import/discovery-settings");
                if (response.ok) {
                    setSettings((await response.json()) as DiscoverySettings);
                }
            } catch {
                // Limites affichées seulement si disponibles ; le serveur les applique de toute façon.
            }
        })();
    }, []);

    useEffect(() => {
        setRun(null);
        void (async () => {
            try {
                const response = await apiFetch(`/api/v1/admin/import/discover-agendas/latest?scope=${scope}`);
                if (response.status === 200) {
                    setRun((await response.json()) as AgendaDiscoveryResult);
                }
            } catch {
                // Pas d'état précédent affiché : la prochaine découverte le donnera.
            }
        })();
    }, [scope]);

    const loadCatalog = useCallback(async () => {
        setCatalogError(null);
        const params = new URLSearchParams({ filter, page: String(page), size: String(CATALOG_PAGE_SIZE) });
        if (appliedQuery.trim()) {
            params.set("q", appliedQuery.trim());
        }
        try {
            const response = await apiFetch(`/api/v1/admin/import/discovered-agendas?${params.toString()}`);
            if (!response.ok) {
                setCatalogError(await errorMessage(response, "Le catalogue des agendas n'a pas pu être chargé."));
                return;
            }
            setCatalog((await response.json()) as CatalogPage);
        } catch {
            setCatalogError("Impossible de contacter le serveur, réessaie plus tard.");
        }
    }, [filter, page, appliedQuery]);

    useEffect(() => {
        void loadCatalog();
    }, [loadCatalog]);

    /** Un lot ; renvoie le bilan, ou `null` en cas d'erreur (affichée). */
    async function discoverBatch(restart: boolean): Promise<AgendaDiscoveryResult | null> {
        const params = new URLSearchParams({ scope });
        if (restart) {
            params.set("restart", "true");
        }
        const response = await apiFetch(`/api/v1/admin/import/discover-agendas?${params.toString()}`, {
            method: "POST",
        });
        if (!response.ok) {
            setDiscoveryError(
                response.status === 409
                    ? "Une découverte est déjà en cours (lancée par un autre administrateur). Réessaie à la fin de son lot."
                    : await errorMessage(response, "La découverte des agendas a échoué."),
            );
            return null;
        }
        const result = (await response.json()) as AgendaDiscoveryResult;
        setRun(result);
        return result;
    }

    async function handleDiscover(event: FormEvent, restart = false) {
        event.preventDefault();
        if (restart && !window.confirm("Abandonner le parcours en cours et repartir du début de l'annuaire ?")) {
            return;
        }
        setDiscoveryError(null);
        setRunning(true);
        stopRequested.current = false;
        try {
            let previousErrors = restart ? 0 : (run?.errors ?? 0);
            let result = await discoverBatch(restart);
            // Enchaînement : seulement tant que le lot s'est arrêté sur sa limite de pages, sans nouvelle erreur
            // (un échec d'OpenAgenda laisse le parcours reprenable, mais ne doit pas être relancé en boucle).
            while (untilEnd && result && result.resumable && result.errors <= previousErrors && !stopRequested.current) {
                previousErrors = result.errors;
                result = await discoverBatch(false);
            }
            if (result && result.newAgendas > 0) {
                setPage(0);
                await loadCatalog();
            }
        } catch {
            setDiscoveryError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setRunning(false);
        }
    }

    function toggle(agendaUid: string) {
        setSelected((current) => {
            const next = new Set(current);
            if (next.has(agendaUid)) {
                next.delete(agendaUid);
            } else {
                next.add(agendaUid);
            }
            return next;
        });
    }

    const maxActivation = settings?.maxActivationBatch ?? null;
    const tooMany = maxActivation !== null && selected.size > maxActivation;

    async function handleActivate() {
        if (selected.size === 0 || tooMany) {
            return;
        }
        if (
            !window.confirm(
                `Créer ${selected.size} source(s) active(s) ? Elles seront collectées au prochain import (planifié chaque heure).`,
            )
        ) {
            return;
        }
        setActivating(true);
        setActivation(null);
        setActivationError(null);
        try {
            const response = await apiFetch("/api/v1/admin/import/discovered-agendas/sources", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ agendaUids: [...selected] }),
            });
            if (!response.ok) {
                setActivationError(await errorMessage(response, "La création des sources a échoué."));
                return;
            }
            const result = (await response.json()) as ActivationResult;
            setActivation(result);
            setSelected(new Set());
            if (result.created.length > 0) {
                onSourcesChanged();
            }
            await loadCatalog();
        } catch {
            setActivationError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setActivating(false);
        }
    }

    const pageCount = catalog ? Math.max(1, Math.ceil(catalog.total / CATALOG_PAGE_SIZE)) : 1;

    return (
        <section aria-labelledby="admin-discovery-title" className="admin-import-card">
            <h2 id="admin-discovery-title">Découvrir les agendas OpenAgenda</h2>
            <p className="admin-import-hint">
                Parcourt l'annuaire OpenAgenda accessible avec la clé de LocalLife, par lots de{" "}
                {settings?.defaultPagesPerBatch ?? "…"} page(s) de 100 agendas, et les range dans un catalogue.
                Aucune source n'est créée et aucune activité n'est importée à cette étape : choisis ensuite
                les agendas à collecter dans le catalogue ci-dessous.
            </p>
            <form className="admin-import-form" onSubmit={(event) => void handleDiscover(event)}>
                <label>
                    Périmètre{" "}
                    <select
                        disabled={running}
                        onChange={(event) => setScope(event.target.value as DiscoveryScope)}
                        value={scope}
                    >
                        <option value="ALL">Tous les agendas accessibles</option>
                        <option value="OFFICIAL">Agendas officiels seulement</option>
                    </select>
                </label>
                <label>
                    <input
                        checked={untilEnd}
                        disabled={running}
                        onChange={(event) => setUntilEnd(event.target.checked)}
                        type="checkbox"
                    />{" "}
                    Enchaîner les lots jusqu'à la fin
                </label>
                <button className="admin-import-button" disabled={running} type="submit">
                    {running ? (
                        <>
                            <span aria-hidden="true" className="admin-import-spinner" />
                            Découverte en cours…
                        </>
                    ) : run?.resumable ? (
                        "Poursuivre la découverte"
                    ) : (
                        "Lancer la découverte"
                    )}
                </button>
                {running && untilEnd && (
                    <button className="admin-import-button" onClick={() => {
                            stopRequested.current = true;
                        }} type="button">
                        Arrêter après ce lot
                    </button>
                )}
                {!running && run?.resumable && (
                    <button className="admin-import-button" onClick={(event) => void handleDiscover(event, true)} type="button">
                        Recommencer depuis le début
                    </button>
                )}
            </form>
            {discoveryError && (
                <p className="admin-import-message admin-import-message-error" role="alert">
                    {discoveryError}
                </p>
            )}
            {run && (
                <div className={`admin-import-message ${STATUS_CLASSES[run.status]}`} role="status">
                    <p>
                        <strong>{STATUS_LABELS[run.status]}</strong> — {run.pagesRead} page(s) lue(s),{" "}
                        {run.examined} agenda(s) examiné(s){run.providerTotal !== null ? ` sur ${run.providerTotal} annoncé(s)` : ""} :{" "}
                        {run.newAgendas} nouveau(x), {run.alreadyKnown} déjà connu(s), {run.ignored} ignoré(s),{" "}
                        {run.errors} erreur(s).
                    </p>
                    {run.stopReason && <p>{run.stopReason}</p>}
                </div>
            )}

            <h3 className="admin-discovery-subtitle">Catalogue des agendas découverts</h3>
            <form
                className="admin-import-form"
                onSubmit={(event) => {
                    event.preventDefault();
                    setPage(0);
                    setAppliedQuery(query);
                }}
            >
                <label>
                    Afficher{" "}
                    <select
                        onChange={(event) => {
                            setFilter(event.target.value as CatalogFilter);
                            setPage(0);
                        }}
                        value={filter}
                    >
                        <option value="NEW">Sans source</option>
                        <option value="KNOWN">Déjà en source</option>
                        <option value="ALL">Tous</option>
                    </select>
                </label>
                <label>
                    Recherche{" "}
                    <input
                        className="admin-discovery-search"
                        onChange={(event) => setQuery(event.target.value)}
                        placeholder="nom, slug ou identifiant"
                        type="search"
                        value={query}
                    />
                </label>
                <button className="admin-import-button" type="submit">
                    Filtrer
                </button>
            </form>
            {catalogError && (
                <p className="admin-import-message admin-import-message-error" role="alert">
                    {catalogError}
                </p>
            )}
            {catalog && catalog.total === 0 && (
                <p className="admin-import-hint">Aucun agenda dans le catalogue pour ce filtre.</p>
            )}
            {catalog && catalog.total > 0 && (
                <>
                    <div className="admin-import-table-wrapper">
                        <table className="admin-import-table">
                            <thead>
                                <tr>
                                    <th scope="col">Choisir</th>
                                    <th scope="col">Agenda</th>
                                    <th scope="col">Identifiant</th>
                                    <th scope="col">Officiel</th>
                                    <th scope="col">Source</th>
                                </tr>
                            </thead>
                            <tbody>
                                {catalog.items.map((item) => (
                                    <tr key={item.agendaUid}>
                                        <td>
                                            {item.sourceId === null ? (
                                                <input
                                                    aria-label={`Choisir ${item.title ?? item.agendaUid}`}
                                                    checked={selected.has(item.agendaUid)}
                                                    onChange={() => toggle(item.agendaUid)}
                                                    type="checkbox"
                                                />
                                            ) : null}
                                        </td>
                                        <th scope="row">
                                            {item.url ? (
                                                <a href={item.url} rel="noreferrer" target="_blank">
                                                    {item.title ?? item.agendaUid}
                                                </a>
                                            ) : (
                                                (item.title ?? item.agendaUid)
                                            )}
                                        </th>
                                        <td>{item.agendaUid}</td>
                                        <td>{item.official === null ? "?" : item.official ? "oui" : "non"}</td>
                                        <td>
                                            {item.sourceId === null
                                                ? "—"
                                                : `${item.sourceName ?? ""} (${item.sourceStatus === "ACTIVE" ? "active" : "inactive"})`}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="admin-import-form admin-discovery-pager">
                        <button
                            className="admin-import-button"
                            disabled={page === 0}
                            onClick={() => setPage((current) => current - 1)}
                            type="button"
                        >
                            Précédent
                        </button>
                        <span>
                            Page {page + 1} / {pageCount} — {catalog.total} agenda(s)
                        </span>
                        <button
                            className="admin-import-button"
                            disabled={page + 1 >= pageCount}
                            onClick={() => setPage((current) => current + 1)}
                            type="button"
                        >
                            Suivant
                        </button>
                    </div>
                </>
            )}
            <div className="admin-import-form">
                <button
                    className="admin-import-button"
                    disabled={selected.size === 0 || tooMany || activating}
                    onClick={() => void handleActivate()}
                    type="button"
                >
                    {activating ? "Création en cours…" : `Créer les sources sélectionnées (${selected.size})`}
                </button>
                {maxActivation !== null && (
                    <span className={tooMany ? "admin-discovery-limit-exceeded" : "admin-import-hint"}>
                        Au plus {maxActivation} par lot{tooMany ? " : retire des agendas de la sélection." : "."}
                    </span>
                )}
            </div>
            {activationError && (
                <p className="admin-import-message admin-import-message-error" role="alert">
                    {activationError}
                </p>
            )}
            {activation && (
                <div className="admin-import-message admin-import-message-success" role="status">
                    <p>
                        <strong>{activation.created.length} source(s) créée(s)</strong>
                        {activation.alreadyKnown.length > 0 ? `, ${activation.alreadyKnown.length} déjà existante(s) (laissée(s) telle(s) quelle(s))` : ""}
                        {activation.notFound.length > 0 ? `, ${activation.notFound.length} absente(s) du catalogue` : ""}. Elles
                        seront collectées au prochain import.
                    </p>
                </div>
            )}
        </section>
    );
}

export default AdminAgendaDiscovery;
