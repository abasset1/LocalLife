import { useEffect, useState } from "react";
import { apiFetch } from "../../api/apiClient";

/**
 * Doublons de lieux d'événement (ADR-0011, § 7.2.13) — LECTURE SEULE. Un même lieu réel a parfois plusieurs
 * lignes : les doublons certains (même texte à la casse, la ponctuation et l'ordre des mots près) sont regroupés,
 * les probables (un texte contenu dans l'autre) sont présentés paire par paire, à relire. Rien n'est fusionné ici ;
 * depuis l'option A, les imports ne créent plus de doublons certains.
 */

export type DuplicateLevel = "CERTAIN" | "PROBABLE";

interface DuplicateLocation {
    id: number;
    name: string | null;
    address: string | null;
    postalCode: string | null;
    city: string | null;
    coordinatePrecision: string;
    activityCount: number;
    linkedPlaceId: number | null;
}

interface DuplicateGroup {
    level: DuplicateLevel;
    maxDistanceMeters: number;
    activityCount: number;
    locations: DuplicateLocation[];
}

interface DuplicateReport {
    clustersByLevel: Partial<Record<DuplicateLevel, number>>;
    locationsByLevel: Partial<Record<DuplicateLevel, number>>;
    extraRowsByLevel: Partial<Record<DuplicateLevel, number>>;
    activitiesByLevel: Partial<Record<DuplicateLevel, number>>;
    clusters: DuplicateGroup[];
}

const LEVELS: { value: DuplicateLevel; label: string; help: string }[] = [
    {
        value: "CERTAIN",
        label: "Certains",
        help: "Même texte à la casse, la ponctuation et l'ordre des mots près, à 30 m au plus.",
    },
    {
        value: "PROBABLE",
        label: "Probables",
        help: "Un texte contenu dans l'autre, ou même texte plus loin : souvent un vrai doublon, parfois une "
            + "salle d'un même bâtiment ou une place et le lieu qui s'y trouve. À relire.",
    },
];

const LIMIT = 200;

function label(location: DuplicateLocation): string {
    return [location.name, location.address].filter(Boolean).join(" — ") || `Lieu ${location.id}`;
}

function AdminEventLocationDuplicatesSection() {
    const [department, setDepartment] = useState("84");
    const [level, setLevel] = useState<DuplicateLevel>("CERTAIN");
    const [report, setReport] = useState<DuplicateReport | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        const params = new URLSearchParams({ level, limit: String(LIMIT) });
        if (department.trim()) {
            params.set("department", department.trim());
        }
        apiFetch(`/api/v1/admin/places/event-location-duplicates?${params.toString()}`, { signal: controller.signal })
            .then(async (response) => {
                if (!response.ok) {
                    throw new Error(`Erreur ${response.status}`);
                }
                setReport((await response.json()) as DuplicateReport);
                setLoadError(null);
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) {
                    setLoadError(error instanceof Error ? error.message : "Erreur de chargement");
                }
            });
        return () => controller.abort();
    }, [department, level]);

    const groups = report?.clusters ?? [];
    const current = LEVELS.find((item) => item.value === level);

    return (
        <section aria-labelledby="admin-duplicates-title">
            <h2 id="admin-duplicates-title">Doublons de lieux d'événement</h2>
            <p>
                Un même lieu réel peut avoir plusieurs lignes. Cet écran les montre sans rien modifier ; les activités
                restent affichées normalement.
            </p>

            <nav aria-label="Niveau de doublon" className="admin-tabs">
                {LEVELS.map((tab) => (
                    <button
                        className={tab.value === level ? "admin-tab admin-tab-active" : "admin-tab"}
                        key={tab.value}
                        onClick={() => setLevel(tab.value)}
                        type="button"
                    >
                        {tab.label} ({report?.clustersByLevel[tab.value] ?? 0})
                    </button>
                ))}
            </nav>
            {current && <p className="activities-status">{current.help}</p>}

            <div className="admin-places-filters">
                <label>
                    Département
                    <input
                        inputMode="numeric"
                        maxLength={3}
                        onChange={(event) => setDepartment(event.target.value)}
                        placeholder="ex. 84"
                        value={department}
                    />
                </label>
            </div>

            {report && (
                <p className="activities-status" role="status">
                    {level === "CERTAIN"
                        ? `${report.clustersByLevel.CERTAIN ?? 0} lieu(x) en double, ${report.extraRowsByLevel.CERTAIN ?? 0} ligne(s) en trop, ${report.activitiesByLevel.CERTAIN ?? 0} activité(s) concernée(s).`
                        : `${report.clustersByLevel.PROBABLE ?? 0} paire(s) à relire, ${report.activitiesByLevel.PROBABLE ?? 0} activité(s) concernée(s).`}
                </p>
            )}
            {loadError && <p className="activities-status activities-status-error" role="alert">{loadError}</p>}
            {report && groups.length === 0 && <p className="activities-status">Aucun doublon pour ces critères.</p>}

            <ul className="admin-activity-list">
                {groups.map((group) => (
                    <li className="admin-activity-card" key={group.locations.map((location) => location.id).join("-")}>
                        <div className="admin-activity-header">
                            <h3>{label(group.locations[0])}</h3>
                            <span>
                                {group.locations.length} lignes · {group.maxDistanceMeters} m · {group.activityCount} activité(s)
                            </span>
                        </div>
                        <ul className="admin-duplicate-locations">
                            {group.locations.map((location) => (
                                <li key={location.id}>
                                    <strong>n° {location.id}</strong> {label(location)}
                                    {location.city ? `, ${location.city}` : ""} · {location.activityCount} activité(s)
                                    {location.linkedPlaceId !== null && ` · relié au lieu permanent n° ${location.linkedPlaceId}`}
                                </li>
                            ))}
                        </ul>
                    </li>
                ))}
            </ul>
        </section>
    );
}

export default AdminEventLocationDuplicatesSection;
