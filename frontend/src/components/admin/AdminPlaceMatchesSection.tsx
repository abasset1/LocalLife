import { useEffect, useState } from "react";
import { apiFetch } from "../../api/apiClient";
import { placeTypeLabel } from "../places/placeTypes";

/**
 * Rapprochements lieux d'événement ↔ lieux permanents (ADR-0011, § 7.2.9). Le rapport classe les paires
 * (certaines, probables, ambiguës) ; un administrateur relie les paires qu'il valide. Le lien est réversible
 * (« Délier ») et ne modifie ni le lieu d'événement ni ses programmations. Rien n'est relié automatiquement.
 */

export type MatchLevel = "CERTAIN" | "PROBABLE" | "AMBIGUOUS";

export interface PlaceMatch {
    level: MatchLevel;
    reason: string | null;
    distanceMeters: number;
    eventLocationId: number;
    eventLocationName: string | null;
    eventLocationAddress: string | null;
    eventLocationCity: string | null;
    activityCount: number;
    placeId: number;
    placeName: string;
    placeAddress: string | null;
    placeType: string;
    placeStatus: string;
    linkedPlaceId: number | null;
}

interface MatchReport {
    pairsByLevel: Partial<Record<MatchLevel, number>>;
    eventLocationsByLevel: Partial<Record<MatchLevel, number>>;
    activitiesByLevel: Partial<Record<MatchLevel, number>>;
    matches: PlaceMatch[];
}

interface LinkResult {
    linked: { eventLocationId: number; placeId: number }[];
    refused: { eventLocationId: number; placeId: number; reason: string }[];
}

const LEVELS: { value: MatchLevel; label: string }[] = [
    { value: "CERTAIN", label: "Certaines" },
    { value: "PROBABLE", label: "Probables" },
    { value: "AMBIGUOUS", label: "Ambiguës" },
];

const LIMIT = 200;

function pairKey(match: Pick<PlaceMatch, "eventLocationId" | "placeId">): string {
    return `${match.eventLocationId}-${match.placeId}`;
}

function AdminPlaceMatchesSection() {
    const [department, setDepartment] = useState("84");
    const [level, setLevel] = useState<MatchLevel>("CERTAIN");
    const [report, setReport] = useState<MatchReport | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        const params = new URLSearchParams({ level, limit: String(LIMIT) });
        if (department.trim()) {
            params.set("department", department.trim());
        }
        apiFetch(`/api/v1/admin/places/matches?${params.toString()}`, { signal: controller.signal })
            .then(async (response) => {
                if (!response.ok) {
                    throw new Error(`Erreur ${response.status}`);
                }
                setReport((await response.json()) as MatchReport);
                setLoadError(null);
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) {
                    setLoadError(error instanceof Error ? error.message : "Erreur de chargement");
                }
            });
        return () => controller.abort();
    }, [department, level, refreshKey]);

    const matches = report?.matches ?? [];
    const linkable = matches.filter((match) => match.linkedPlaceId !== match.placeId);

    function changeFilter(update: () => void) {
        update();
        setSelected(new Set());
        setMessage(null);
    }

    function toggle(match: PlaceMatch) {
        setSelected((current) => {
            const next = new Set(current);
            const key = pairKey(match);
            if (next.has(key)) {
                next.delete(key);
            } else {
                next.add(key);
            }
            return next;
        });
    }

    async function linkSelection() {
        const links = linkable
            .filter((match) => selected.has(pairKey(match)))
            .map((match) => ({ eventLocationId: match.eventLocationId, placeId: match.placeId }));
        if (links.length === 0 || !window.confirm(`Relier ${links.length} lieu(x) d'événement ?`)) {
            return;
        }
        setBusy(true);
        try {
            const response = await apiFetch("/api/v1/admin/places/links", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ links }),
            });
            if (!response.ok) {
                throw new Error(`Erreur ${response.status}`);
            }
            const result = (await response.json()) as LinkResult;
            const refused = result.refused.map((item) => `${item.eventLocationId} : ${item.reason}`);
            setMessage(`${result.linked.length} reliée(s)${refused.length ? ` ; refusées — ${refused.join(" ; ")}` : ""}.`);
            setSelected(new Set());
            setRefreshKey((key) => key + 1);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Erreur");
        } finally {
            setBusy(false);
        }
    }

    async function unlink(match: PlaceMatch) {
        if (!window.confirm("Délier ce lieu d'événement ?")) {
            return;
        }
        setBusy(true);
        try {
            const response = await apiFetch(`/api/v1/admin/places/links/${match.eventLocationId}`, { method: "DELETE" });
            setMessage(response.ok ? "Lien retiré." : `Erreur ${response.status}`);
            setRefreshKey((key) => key + 1);
        } finally {
            setBusy(false);
        }
    }

    return (
        <section aria-labelledby="admin-matches-title">
            <h2 id="admin-matches-title">Rapprochements avec les lieux permanents</h2>
            <p>
                Paires proposées par la règle de rapprochement. Relier une paire ne modifie ni le lieu de l'événement ni
                ses dates : le lien se retire à tout moment. À vérifier avant de relier, surtout les paires probables et
                ambiguës.
            </p>

            <nav aria-label="Niveau de rapprochement" className="admin-tabs">
                {LEVELS.map((tab) => (
                    <button
                        className={tab.value === level ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={tab.value}
                        onClick={() => changeFilter(() => setLevel(tab.value))}
                        type="button"
                    >
                        {tab.label} ({report?.pairsByLevel[tab.value] ?? 0})
                    </button>
                ))}
            </nav>

            <div className="admin-places-filters">
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
            </div>

            <div className="admin-places-bulk">
                <span>
                    {matches.length} paire(s) · {selected.size} sélectionnée(s)
                </span>
                <label>
                    <input
                        checked={linkable.length > 0 && linkable.every((match) => selected.has(pairKey(match)))}
                        disabled={linkable.length === 0}
                        onChange={(event) =>
                            setSelected(event.target.checked ? new Set(linkable.map(pairKey)) : new Set())
                        }
                        type="checkbox"
                    />{" "}
                    Toutes les paires non reliées
                </label>
                <button
                    className="admin-approve-button"
                    disabled={busy || selected.size === 0}
                    onClick={() => void linkSelection()}
                    type="button"
                >
                    Relier la sélection
                </button>
            </div>
            {message && <p className="activities-status" role="status">{message}</p>}
            {loadError && <p className="activities-status activities-status-error" role="alert">{loadError}</p>}
            {report && matches.length === 0 && <p className="activities-status">Aucune paire pour ces critères.</p>}

            <ul className="admin-activity-list">
                {matches.map((match) => {
                    const linkedHere = match.linkedPlaceId === match.placeId;
                    const eventLabel = match.eventLocationName || match.eventLocationAddress || `Lieu ${match.eventLocationId}`;
                    return (
                        <li className="admin-activity-card" key={pairKey(match)}>
                            <div className="admin-activity-header">
                                <label className="admin-place-select">
                                    {!linkedHere && (
                                        <input
                                            aria-label={`Sélectionner ${eventLabel} → ${match.placeName}`}
                                            checked={selected.has(pairKey(match))}
                                            onChange={() => toggle(match)}
                                            type="checkbox"
                                        />
                                    )}
                                    <h3>{match.placeName}</h3>
                                </label>
                                <span>
                                    {placeTypeLabel(match.placeType)} · {match.distanceMeters} m
                                </span>
                            </div>
                            <dl className="admin-activity-meta">
                                <dt>Lieu de l'événement</dt>
                                <dd>{[eventLabel, match.eventLocationCity].filter(Boolean).join(", ")}</dd>
                                <dt>Activités</dt>
                                <dd>{match.activityCount}</dd>
                                <dt>Lieu permanent</dt>
                                <dd>{[match.placeAddress, match.placeStatus === "PUBLISHED" ? "publié" : "en attente"].filter(Boolean).join(" · ")}</dd>
                                {match.reason && (
                                    <>
                                        <dt>À trancher</dt>
                                        <dd>{match.reason}</dd>
                                    </>
                                )}
                                {match.linkedPlaceId !== null && !linkedHere && (
                                    <>
                                        <dt>Déjà relié</dt>
                                        <dd>à un autre lieu permanent (n° {match.linkedPlaceId})</dd>
                                    </>
                                )}
                            </dl>
                            {linkedHere && (
                                <div className="admin-activity-actions">
                                    <span>Relié</span>
                                    <button className="admin-reject-button" disabled={busy} onClick={() => void unlink(match)} type="button">
                                        Délier
                                    </button>
                                </div>
                            )}
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

export default AdminPlaceMatchesSection;
